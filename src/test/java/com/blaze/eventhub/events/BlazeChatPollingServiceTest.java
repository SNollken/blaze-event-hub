package com.blaze.eventhub.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blaze.eventhub.blaze.BlazeApiClient;
import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.participant.BlazeChatMessageParser;
import com.blaze.eventhub.event.participant.CaptureResult;
import com.blaze.eventhub.event.participant.CaptureStatus;
import com.blaze.eventhub.event.participant.ChatEntryCandidate;
import com.blaze.eventhub.event.participant.EventParticipantCaptureService;
import com.blaze.eventhub.oauth.PersistentOAuthCredentialService;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.config.BlazeProperties;

class BlazeChatPollingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");

    private EventStore eventStore;
    private PersistentOAuthCredentialService credentialService;
    private BlazeApiClient apiClient;
    private BlazeChatMessageParser parser;
    private EventParticipantCaptureService captureService;
    private ChatPollingCursorStore cursorStore;
    private BlazeProperties properties;
    private BlazeChatPollingService service;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStore.class);
        credentialService = mock(PersistentOAuthCredentialService.class);
        apiClient = mock(BlazeApiClient.class);
        parser = mock(BlazeChatMessageParser.class);
        captureService = mock(EventParticipantCaptureService.class);
        cursorStore = mock(ChatPollingCursorStore.class);
        properties = new BlazeProperties();
        service = new BlazeChatPollingService(
                eventStore,
                credentialService,
                apiClient,
                parser,
                captureService,
                cursorStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties);
    }

    @Test
    void pollsCreatorChannelOnceAndCapturesMessagesIdempotently() {
        Event firstCommand = openEvent("event-1", "!participar");
        when(eventStore.findByStatus(EventStatus.OPEN)).thenReturn(List.of(firstCommand));
        when(eventStore.findCapturingByChannelId("channel-1"))
                .thenReturn(List.of(firstCommand));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> response = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(response);
        ChatEntryCandidate first = candidate("message-1", "user-1", "!participar");
        ChatEntryCandidate second = candidate("message-2", "user-2", "!participar");
        when(parser.parse("channel-1", response)).thenReturn(List.of(first, second));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.empty());
        when(captureService.capture(first)).thenReturn(
                new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));
        when(captureService.capture(second)).thenReturn(
                new CaptureResult(CaptureStatus.DUPLICATE, "event-1", "entry_already_registered"));

        PollingCycleResult result = service.pollNow();

        assertEquals(1, result.channelsPolled());
        assertEquals(2, result.messagesSeen());
        assertEquals(1, result.acceptedEntries());
        assertEquals(1, result.duplicateEntries());
        assertEquals(0, result.failures());
        verify(apiClient).getChatMessages("channel-1", token);
        verify(captureService).capture(first);
        verify(captureService).capture(second);
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", "message-1", NOW);
    }

    @Test
    void pollsOneEventOnDemandBeforeFinalization() {
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> response = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(response);
        ChatEntryCandidate candidate = candidate("message-final", "user-final", "!participar");
        when(parser.parse("channel-1", response)).thenReturn(List.of(candidate));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.empty());
        when(captureService.capture(candidate)).thenReturn(
                new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.channelsPolled());
        assertEquals(1, result.acceptedEntries());
        verify(eventStore, never()).findByStatus(EventStatus.OPEN);
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", "message-final", NOW);
    }

    @Test
    void rejectsOnDemandPollingForAnotherCreator() {
        when(eventStore.findById("event-1")).thenReturn(Optional.of(openEvent("event-1", "!participar")));

        org.junit.jupiter.api.Assertions.assertThrows(
                ForbiddenException.class,
                () -> service.pollEvent("event-1", "other-member"));

        verify(apiClient, never()).getChatMessages(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(TokenSnapshot.class));
    }

    @Test
    void finalSynchronizationDoesNotAdvanceCursorPastPersistedCutoff() {
        Event event = finalizingEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> response = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(response);
        ChatEntryCandidate before = candidate(
                "message-before", "user-before", "!participar", NOW.minusMillis(1));
        ChatEntryCandidate after = candidate(
                "message-after", "user-after", "!participar", NOW.plusMillis(1));
        when(parser.parse("channel-1", response)).thenReturn(List.of(before, after));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.empty());
        when(captureService.capture(before)).thenReturn(
                new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.messagesSeen());
        assertEquals(1, result.acceptedEntries());
        verify(captureService).capture(before);
        verify(captureService, never()).capture(after);
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", "message-before", NOW);
    }

    @Test
    void followsPaginationUntilItFindsThePersistedCursor() {
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> recentPage = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of("cursor", "older-page")));
        Map<String, Object> olderPage = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(recentPage);
        when(apiClient.getChatMessages("channel-1", "older-page", token)).thenReturn(olderPage);
        ChatEntryCandidate recent = candidate(
                "message-recent", "user-recent", "!participar", NOW);
        ChatEntryCandidate persisted = candidate(
                "message-persisted", "user-old", "!participar", NOW.minusSeconds(1));
        when(parser.parse("channel-1", recentPage)).thenReturn(List.of(recent));
        when(parser.parse("channel-1", olderPage)).thenReturn(List.of(persisted));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.of(
                new ChatPollingCursor(
                        "creator-member", "channel-1", "event-1", "message-persisted",
                        null, null, NOW.minusSeconds(2), NOW.minusSeconds(2), null)));
        when(captureService.capture(recent)).thenReturn(
                new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.messagesSeen());
        assertEquals(1, result.acceptedEntries());
        verify(apiClient).getChatMessages("channel-1", token);
        verify(apiClient).getChatMessages("channel-1", "older-page", token);
        verify(captureService).capture(recent);
        verify(captureService, never()).capture(persisted);
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", "message-recent", NOW);
    }

    @Test
    void capturesNewMessageWithSameTimestampAndLexicographicallySmallerIdThanBoundary() {
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> response = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(response);
        ChatEntryCandidate newMessage = candidate(
                "a-new-message", "user-new", "!participar", NOW);
        ChatEntryCandidate boundary = candidate(
                "z-persisted-boundary", "user-old", "!participar", NOW);
        when(parser.parse("channel-1", response)).thenReturn(List.of(newMessage, boundary));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.of(
                new ChatPollingCursor(
                        "creator-member", "channel-1", "event-1", "z-persisted-boundary",
                        null, null, NOW.minusSeconds(2), NOW.minusSeconds(2), null)));
        when(captureService.capture(newMessage)).thenReturn(
                new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.messagesSeen());
        assertEquals(1, result.acceptedEntries());
        verify(captureService).capture(newMessage);
        verify(captureService, never()).capture(boundary);
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", "a-new-message", NOW);
    }

    @Test
    void checkpointsBackfillWhenPaginationExceedsThePerCycleLimit() {
        properties.setChatMaxPagesPerPoll(1);
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> response = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of("cursor", "more")));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(response);
        when(parser.parse("channel-1", response)).thenReturn(List.of(
                candidate("message-1", "user-1", "!participar", NOW)));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.empty());
        when(captureService.capture(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.failures());
        assertEquals(1, result.acceptedEntries());
        verify(cursorStore).markBackfillProgress(
                "creator-member", "channel-1", "event-1", null,
                "more", "message-1", NOW);
        verify(cursorStore, never()).markFailure(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resumesBackfillAndRequiresAHeadRefreshAfterFindingTheBoundary() {
        properties.setChatMaxPagesPerPoll(1);
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> olderPage = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", "older-page", token)).thenReturn(olderPage);
        ChatEntryCandidate middle = candidate(
                "message-middle", "user-middle", "!participar", NOW.minusSeconds(1));
        ChatEntryCandidate boundary = candidate(
                "message-persisted", "user-old", "!participar", NOW.minusSeconds(2));
        when(parser.parse("channel-1", olderPage)).thenReturn(List.of(middle, boundary));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.of(
                new ChatPollingCursor(
                        "creator-member", "channel-1", "event-1", "message-persisted",
                        "older-page", "message-recent",
                        NOW.minusSeconds(2), NOW.minusSeconds(2), "CHAT_BACKFILL_PENDING")));
        when(captureService.capture(middle)).thenReturn(
                new CaptureResult(CaptureStatus.ACCEPTED, "event-1", "entry_accepted"));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.failures());
        assertEquals(1, result.acceptedEntries());
        verify(apiClient, never()).getChatMessages("channel-1", token);
        verify(apiClient).getChatMessages("channel-1", "older-page", token);
        verify(captureService).capture(middle);
        verify(captureService, never()).capture(boundary);
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", "message-recent", NOW);
        verify(cursorStore).markFailure(
                "creator-member", "channel-1", "event-1", "CHAT_HEAD_REFRESH_REQUIRED", NOW);
    }

    @Test
    void refusesSilentSuccessWhenHistoryEndsBeforeThePersistedBoundary() {
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> truncatedPage = Map.of(
                "data", Map.of("messages", List.of(), "pagination", Map.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(truncatedPage);
        when(parser.parse("channel-1", truncatedPage)).thenReturn(List.of(
                candidate("message-new", "user-new", "!participar", NOW)));
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.of(
                new ChatPollingCursor(
                        "creator-member", "channel-1", "event-1", "missing-boundary",
                        null, null, NOW.minusSeconds(2), NOW.minusSeconds(2), null)));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(1, result.failures());
        verify(captureService, never()).capture(org.mockito.ArgumentMatchers.any());
        verify(cursorStore).markFailure(
                "creator-member", "channel-1", "event-1", "CHAT_HISTORY_GAP", NOW);
        verify(cursorStore, never()).markSuccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotReuseCursorStateFromAPreviousEventOnTheSameChannel() {
        Event event = openEvent("event-1", "!participar");
        when(eventStore.findById("event-1")).thenReturn(Optional.of(event));
        TokenSnapshot token = new TokenSnapshot(
                "user", "creator-blaze", "Bearer", "access", "refresh",
                NOW.plusSeconds(3600), List.of("users.read"), NOW);
        when(credentialService.currentValid("creator-member")).thenReturn(token);
        Map<String, Object> emptyPage = Map.of("data", Map.of("messages", List.of()));
        when(apiClient.getChatMessages("channel-1", token)).thenReturn(emptyPage);
        when(parser.parse("channel-1", emptyPage)).thenReturn(List.of());
        when(cursorStore.find("creator-member", "channel-1")).thenReturn(Optional.of(
                new ChatPollingCursor(
                        "creator-member", "channel-1", "event-old", "old-message",
                        "old-cursor", "old-anchor",
                        NOW.minusSeconds(2), NOW.minusSeconds(2), null)));

        PollingCycleResult result = service.pollEvent("event-1", "creator-member");

        assertEquals(0, result.failures());
        verify(apiClient).getChatMessages("channel-1", token);
        verify(apiClient, never()).getChatMessages(
                org.mockito.ArgumentMatchers.eq("channel-1"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(token));
        verify(cursorStore).markSuccess(
                "creator-member", "channel-1", "event-1", null, NOW);
    }

    private static ChatEntryCandidate candidate(String messageId, String userId, String message) {
        return candidate(messageId, userId, message, NOW);
    }

    private static ChatEntryCandidate candidate(
            String messageId,
            String userId,
            String message,
            Instant sentAt) {
        return new ChatEntryCandidate(
                "channel-1", messageId, message, userId, userId, userId, "chat", sentAt);
    }

    private static Event openEvent(String eventId, String command) {
        return event(eventId, command, EventStatus.OPEN, null);
    }

    private static Event finalizingEvent(String eventId, String command) {
        return event(eventId, command, EventStatus.FINALIZING, NOW);
    }

    private static Event event(String eventId, String command, EventStatus status, Instant cutoffAt) {
        return new Event(
                eventId,
                "creator-member",
                "creator-blaze",
                "channel-1",
                "creator",
                "Creator",
                null,
                "Giveaway",
                "Description",
                null,
                "Prize",
                command,
                status,
                0,
                null,
                null,
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                cutoffAt,
                status == EventStatus.FINALIZING ? "attempt-1" : null,
                null,
                null);
    }
}
