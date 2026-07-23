package com.blaze.eventhub.event.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.blaze.eventhub.event.CreateEventRequest;
import com.blaze.eventhub.blaze.BlazeChannelResponse;
import com.blaze.eventhub.event.EventResponse;
import com.blaze.eventhub.event.EventService;
import com.blaze.eventhub.event.FinalizationStart;
import com.blaze.eventhub.event.draw.DrawService;
import com.blaze.eventhub.event.draw.EventWinner;

@SpringBootTest
@ActiveProfiles("test")
class EventParticipantCaptureServiceIntegrationTest {

    private static final String CREATOR_MEMBER_ID = "creator-001";
    private static final String CREATOR_BLAZE_ID = "blaze-creator-001";
    private static final String CHANNEL_ID = "channel-001";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventParticipantCaptureService captureService;

    @Autowired
    private EventParticipantStore participantStore;

    @Autowired
    private DrawService drawService;

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM event_draw_results");
        jdbc.update("DELETE FROM event_participants");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM chat_polling_cursors");
        jdbc.update("DELETE FROM members");
        jdbc.update("""
                INSERT INTO members (id, blaze_user_id, blaze_username, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'active', NOW(), NOW())
                """, CREATOR_MEMBER_ID, CREATOR_BLAZE_ID, "creator", "Creator");
    }

    @Test
    void validChatMessageCreatesOneParticipantWithoutHubMemberAccount() {
        EventResponse event = createOpenEvent();
        ChatEntryCandidate candidate = new ChatEntryCandidate(
                CHANNEL_ID,
                "message-001",
                "  !PARTICIPAR  ",
                "viewer-blaze-001",
                "viewer",
                "Viewer One",
                "chat",
                event.openedAt().plusMillis(1));

        CaptureResult first = captureService.capture(candidate);
        CaptureResult duplicate = captureService.capture(candidate);

        assertEquals(CaptureStatus.ACCEPTED, first.status());
        assertEquals(CaptureStatus.DUPLICATE, duplicate.status());
        assertEquals(event.id(), first.eventId());

        List<EventParticipant> participants = participantStore.findByEventId(event.id());
        assertEquals(1, participants.size());
        assertEquals("viewer-blaze-001", participants.get(0).blazeUserId());
        assertEquals("Viewer One", participants.get(0).displayName());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM members WHERE blaze_user_id = ?",
                Integer.class,
                "viewer-blaze-001"));
    }

    @Test
    void finalizationFreezesParticipantPoolAndRejectsLateEntries() {
        EventResponse event = createOpenEvent();
        CaptureResult initialEntry = captureService.capture(candidate(
                "message-before",
                "viewer-before",
                event.openedAt()));

        FinalizationStart start = eventService.beginFinalization(event.id(), CREATOR_MEMBER_ID);
        CaptureResult finalSyncEntry = captureService.capture(candidate(
                "message-final-sync",
                "viewer-final-sync",
                start.event().finalizationCutoffAt()));
        CaptureResult lateEntry = captureService.capture(candidate(
                "message-after",
                "viewer-after",
                start.event().finalizationCutoffAt().plusMillis(1)));
        EventResponse finalized = eventService.completeFinalization(
                event.id(), CREATOR_MEMBER_ID, start.attemptId());
        EventResponse repeatedFinalization = eventService
                .beginFinalization(event.id(), CREATOR_MEMBER_ID)
                .event();

        assertEquals("closed", finalized.status());
        assertNotNull(finalized.closedAt());
        assertEquals(CaptureStatus.ACCEPTED, initialEntry.status());
        assertEquals(CaptureStatus.ACCEPTED, finalSyncEntry.status());
        assertEquals(2, finalized.finalizedParticipantCount());
        assertEquals(64, finalized.finalizedPoolHash().length());
        assertEquals(CaptureStatus.IGNORED, lateEntry.status());
        assertEquals(2, participantStore.countByEventId(event.id()));
        assertEquals(finalized.finalizedParticipantCount(), repeatedFinalization.finalizedParticipantCount());
        assertEquals(finalized.finalizedPoolHash(), repeatedFinalization.finalizedPoolHash());
        assertEquals(finalized.closedAt(), repeatedFinalization.closedAt());
    }

    @Test
    void drawUsesFrozenBlazePoolAndReturnsSamePersistedResultWhenRepeated() {
        EventResponse event = createOpenEvent();
        captureService.capture(candidate("message-a", "viewer-a"));
        captureService.capture(candidate("message-b", "viewer-b"));
        EventResponse finalized = finishFinalization(event.id());

        EventWinner first = drawService.executeDraw(event.id(), CREATOR_MEMBER_ID);
        EventWinner repeated = drawService.executeDraw(event.id(), CREATOR_MEMBER_ID);

        assertEquals(first.id(), repeated.id());
        assertEquals(event.id(), first.eventId());
        assertEquals(finalized.finalizedPoolHash(), first.poolHash());
        assertEquals(2, first.participantCount());
        assertEquals("weighted_blaze_participants_v1", first.drawMethod());
        assertEquals("completed", eventService.getEvent(event.id()).status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_draw_results WHERE event_id = ?",
                Integer.class,
                event.id()));
    }

    @Test
    void messageSentBeforeEventWasOpenedIsIgnored() {
        EventResponse event = createOpenEvent();
        ChatEntryCandidate historicalMessage = new ChatEntryCandidate(
                CHANNEL_ID,
                "historical-message",
                "!participar",
                "historical-viewer",
                "historical-viewer",
                "Historical Viewer",
                "chat",
                Instant.parse("2020-01-01T00:00:00Z"));

        CaptureResult result = captureService.capture(historicalMessage);

        assertEquals(CaptureStatus.IGNORED, result.status());
        assertEquals(0, participantStore.countByEventId(event.id()));
    }

    @Test
    void messageWithoutTimestampIsIgnoredToAvoidImportingOldChatHistory() {
        EventResponse event = createOpenEvent();
        ChatEntryCandidate undatedMessage = new ChatEntryCandidate(
                CHANNEL_ID, "undated-message", "!participar", "viewer", "viewer", "Viewer", "chat", null);

        CaptureResult result = captureService.capture(undatedMessage);

        assertEquals(CaptureStatus.IGNORED, result.status());
        assertEquals(0, participantStore.countByEventId(event.id()));
    }

    private static ChatEntryCandidate candidate(String messageId, String blazeUserId) {
        return candidate(messageId, blazeUserId, Instant.now());
    }

    private static ChatEntryCandidate candidate(String messageId, String blazeUserId, Instant sentAt) {
        return new ChatEntryCandidate(
                CHANNEL_ID,
                messageId,
                "!participar",
                blazeUserId,
                blazeUserId,
                blazeUserId,
                "chat",
                sentAt);
    }

    private EventResponse finishFinalization(String eventId) {
        FinalizationStart start = eventService.beginFinalization(eventId, CREATOR_MEMBER_ID);
        return eventService.completeFinalization(eventId, CREATOR_MEMBER_ID, start.attemptId());
    }

    private EventResponse createOpenEvent() {
        CreateEventRequest request = new CreateEventRequest(
                "Giveaway do chat",
                "Entrada automática pelo chat da Blaze",
                "Prêmio surpresa",
                "!participar",
                null,
                null,
                "creator");
        EventResponse event = eventService.createEvent(
                request,
                CREATOR_MEMBER_ID,
                CREATOR_BLAZE_ID,
                new BlazeChannelResponse(CHANNEL_ID, "creator", "Creator", null));
        return eventService.openEvent(event.id(), CREATOR_MEMBER_ID);
    }
}
