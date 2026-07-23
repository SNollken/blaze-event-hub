package com.blaze.eventhub.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.common.ConflictException;
import com.blaze.eventhub.blaze.BlazeChannelResponse;
import com.blaze.eventhub.event.participant.ChatEntryCandidate;
import com.blaze.eventhub.event.participant.CaptureStatus;
import com.blaze.eventhub.event.participant.EventParticipantCaptureService;

@SpringBootTest
@ActiveProfiles("test")
class EventServiceIntegrationTest {

    private static final String MEMBER_ID = "member-001";
    private static final String BLAZE_USER_ID = "blaze-user-001";
    private static final String CHANNEL_ID = "channel-001";
    private static final String CHANNEL_SLUG = "creator";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventParticipantCaptureService captureService;

    @Autowired
    private EventStore eventStore;

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
                """, MEMBER_ID, BLAZE_USER_ID, "creator", "Creator");
    }

    @Test
    void createsChatGiveawayWithACommandAndUniformEntryContract() {
        EventResponse response = eventService.createEvent(
                request("Giveaway de aniversario", "  !Participar  "),
                MEMBER_ID,
                BLAZE_USER_ID,
                channel());

        assertNotNull(response.id());
        assertEquals("Giveaway de aniversario", response.title());
        assertEquals("Gift card de R$ 100", response.prize());
        assertEquals("!participar", response.entryCommand());
        assertEquals("draft", response.status());
        assertEquals(CHANNEL_ID, response.creatorChannelId());
        assertEquals(null, response.xPostUrl());
        assertEquals("Gift card de R$ 100", jdbc.queryForObject(
                "SELECT prize FROM events WHERE id = ?", String.class, response.id()));
    }

    @Test
    void persistsReturnsUpdatesAndClearsTheOptionalXPostUrl() {
        String xPostUrl = "https://x.com/creator/status/123456789";
        CreateEventRequest request = new CreateEventRequest(
                "Giveaway com post",
                "Confira o anuncio no X.",
                xPostUrl,
                "Gift card de R$ 100",
                "!participar",
                null,
                null,
                CHANNEL_SLUG);

        EventResponse created = eventService.createEvent(
                request, MEMBER_ID, BLAZE_USER_ID, channel());

        assertEquals(xPostUrl, created.xPostUrl());
        assertEquals(xPostUrl, jdbc.queryForObject(
                "SELECT x_post_url FROM events WHERE id = ?", String.class, created.id()));
        assertEquals(xPostUrl, eventService.getEvent(created.id()).xPostUrl());

        EventResponse preserved = eventService.updateEvent(
                created.id(),
                new UpdateEventRequest("Giveaway renomeado", null, null, null, null, null, null),
                MEMBER_ID);

        assertEquals("Giveaway renomeado", preserved.title());
        assertEquals(xPostUrl, preserved.xPostUrl());

        EventResponse cleared = eventService.updateEvent(
                created.id(),
                new UpdateEventRequest(null, null, "   ", null, null, null, null),
                MEMBER_ID);

        assertEquals(null, cleared.xPostUrl());
        assertEquals(null, jdbc.queryForObject(
                "SELECT x_post_url FROM events WHERE id = ?", String.class, created.id()));
    }

    @Test
    void rejectsLinksThatDoNotPointToAnXPost() {
        CreateEventRequest request = new CreateEventRequest(
                "Giveaway com link invalido",
                null,
                "https://example.com/post/123",
                "Gift card",
                "!participar",
                null,
                null,
                CHANNEL_SLUG);

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(
                request, MEMBER_ID, BLAZE_USER_ID, channel()));

        CreateEventRequest profileOnly = new CreateEventRequest(
                "Giveaway com perfil em vez de post",
                null,
                "https://x.com/creator",
                "Gift card",
                "!participar",
                null,
                null,
                CHANNEL_SLUG);

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(
                profileOnly, MEMBER_ID, BLAZE_USER_ID, channel()));
    }

    @Test
    void requiresTitleAndEntryCommand() {
        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(
                request("", "!participar"), MEMBER_ID, BLAZE_USER_ID, channel()));
        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(
                request("Giveaway", "   "), MEMBER_ID, BLAZE_USER_ID, channel()));
        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(
                request("Giveaway", "participar"), MEMBER_ID, BLAZE_USER_ID, channel()));
        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(
                request("Giveaway", "!"), MEMBER_ID, BLAZE_USER_ID, channel()));
    }

    @Test
    void updatesDraftCommandAndNormalizesIt() {
        EventResponse created = createDefaultEvent("Comando editavel");

        EventResponse updated = eventService.updateEvent(
                created.id(),
                new UpdateEventRequest(null, null, null, "  !EUQUERO  ", null, null),
                MEMBER_ID);

        assertEquals("!euquero", updated.entryCommand());
    }

    @Test
    void refusesBlankPrizeWhenEditingDraft() {
        EventResponse created = createDefaultEvent("Premio obrigatorio");

        assertThrows(IllegalArgumentException.class, () -> eventService.updateEvent(
                created.id(),
                new UpdateEventRequest(null, null, "   ", null, null, null),
                MEMBER_ID));
    }

    @Test
    void refusesEditingAfterCaptureStarts() {
        EventResponse created = createDefaultEvent("Ao vivo");
        eventService.openEvent(created.id(), MEMBER_ID);

        assertThrows(IllegalArgumentException.class, () -> eventService.updateEvent(
                created.id(),
                new UpdateEventRequest("Outro titulo", null, null, null, null, null),
                MEMBER_ID));
    }

    @Test
    void getEventRejectsUnknownId() {
        assertThrows(NotFoundException.class, () -> eventService.getEvent("unknown"));
    }

    @Test
    void keepsDraftsPrivateUntilTheCreatorOpensThem() {
        EventResponse draft = createDefaultEvent("Rascunho privado");

        assertTrue(eventService.listEvents(null).isEmpty());
        assertTrue(eventService.listEvents("draft").isEmpty());
        assertThrows(NotFoundException.class,
                () -> eventService.getVisibleEvent(draft.id(), null));
        assertThrows(NotFoundException.class,
                () -> eventService.getVisibleEventStats(draft.id(), "other-member"));
        assertEquals(draft.id(), eventService.getVisibleEvent(draft.id(), MEMBER_ID).id());

        eventService.openEvent(draft.id(), MEMBER_ID);

        assertEquals(draft.id(), eventService.getVisibleEvent(draft.id(), null).id());
        assertEquals(1, eventService.listEvents(null).size());
    }

    @Test
    void keepsCancelledDraftPrivateButPreservesPublishedCancellationPage() {
        EventResponse privateDraft = createDefaultEvent("Cancelado antes de publicar");
        eventService.cancelEvent(privateDraft.id(), MEMBER_ID);

        assertThrows(NotFoundException.class,
                () -> eventService.getVisibleEvent(privateDraft.id(), null));

        EventResponse published = createDefaultEvent("Cancelado ao vivo");
        eventService.openEvent(published.id(), MEMBER_ID);
        eventService.cancelEvent(published.id(), MEMBER_ID);

        assertEquals(published.id(), eventService.getVisibleEvent(published.id(), null).id());
        assertEquals(1, eventService.listEvents("cancelled").size());
        assertEquals(published.id(), eventService.listEvents("cancelled").getFirst().id());
    }

    @Test
    void onlyOneOpenEventCanCaptureSameCommandInSameChannel() {
        EventResponse first = createDefaultEvent("Primeiro");
        EventResponse second = createDefaultEvent("Segundo");
        eventService.openEvent(first.id(), MEMBER_ID);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.openEvent(second.id(), MEMBER_ID));

        assertEquals("Ja existe um giveaway em captura neste canal.", error.getMessage());
        assertEquals("draft", eventService.getEvent(second.id()).status());
    }

    @Test
    void onlyOneGiveawayCanCapturePerChannelEvenWithDifferentCommands() {
        EventResponse first = createDefaultEvent("Primeiro");
        EventResponse second = eventService.createEvent(
                request("Segundo", "!euquero"), MEMBER_ID, BLAZE_USER_ID, channel());
        eventService.openEvent(first.id(), MEMBER_ID);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.openEvent(second.id(), MEMBER_ID));
        assertEquals("draft", eventService.getEvent(second.id()).status());
    }

    @Test
    void lifecycleStatsFollowCaptureFinalizeAndDrawReadiness() {
        EventResponse event = createDefaultEvent("Ciclo completo");
        EventLifecycleStats draft = eventService.getEventStats(event.id());

        assertFalse(draft.captureActive());
        assertFalse(draft.canFinalize());
        assertFalse(draft.canDraw());
        assertEquals(0, draft.participantCount());

        EventResponse opened = eventService.openEvent(event.id(), MEMBER_ID);
        var capture = captureService.capture(new ChatEntryCandidate(
                CHANNEL_ID,
                "message-001",
                "!participar",
                "viewer-001",
                "viewer",
                "Viewer",
                "chat",
                opened.openedAt().plusMillis(1)));

        EventLifecycleStats open = eventService.getEventStats(event.id());
        assertEquals(CaptureStatus.ACCEPTED, capture.status());
        assertTrue(open.captureActive());
        assertTrue(open.canFinalize());
        assertFalse(open.canDraw());
        assertEquals(1, open.participantCount());
        assertEquals("STARTING", open.captureHealth());

        jdbc.update("""
                INSERT INTO chat_polling_cursors (
                    member_id, channel_id, event_id, last_message_id, last_polled_at,
                    last_success_at, last_error_code)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """, MEMBER_ID, CHANNEL_ID, event.id(), "message-001");
        EventLifecycleStats healthy = eventService.getEventStats(event.id());
        assertEquals("HEALTHY", healthy.captureHealth());
        assertNotNull(healthy.lastSuccessfulPollAt());

        jdbc.update("""
                UPDATE chat_polling_cursors SET last_error_code = 'BLAZE_HTTP_503'
                WHERE member_id = ? AND channel_id = ?
                """, MEMBER_ID, CHANNEL_ID);
        assertEquals("DEGRADED", eventService.getEventStats(event.id()).captureHealth());

        finishFinalization(event.id());
        EventLifecycleStats closed = eventService.getEventStats(event.id());
        assertFalse(closed.captureActive());
        assertFalse(closed.canFinalize());
        assertTrue(closed.canDraw());
        assertEquals(1, closed.finalizedParticipantCount());
        assertEquals("INACTIVE", closed.captureHealth());
    }

    @Test
    void newEventStartsWithoutReusingThePreviousEventsCaptureHealth() {
        EventResponse previous = createDefaultEvent("Evento anterior");
        eventService.openEvent(previous.id(), MEMBER_ID);
        jdbc.update("""
                INSERT INTO chat_polling_cursors (
                    member_id, channel_id, event_id, last_message_id, last_polled_at,
                    last_success_at, last_error_code)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """, MEMBER_ID, CHANNEL_ID, previous.id(), "old-message");
        eventService.cancelEvent(previous.id(), MEMBER_ID);

        EventResponse current = createDefaultEvent("Evento atual");
        eventService.openEvent(current.id(), MEMBER_ID);

        EventLifecycleStats stats = eventService.getEventStats(current.id());
        assertEquals("STARTING", stats.captureHealth());
        assertEquals(null, stats.lastSuccessfulPollAt());
    }

    @Test
    void creatorCanListCapturedBlazeParticipants() {
        EventResponse event = createDefaultEvent("Lista privada");
        EventResponse opened = eventService.openEvent(event.id(), MEMBER_ID);
        var capture = captureService.capture(new ChatEntryCandidate(
                CHANNEL_ID,
                "message-001",
                "!participar",
                "viewer-001",
                "viewer",
                "Viewer One",
                "chat",
                opened.openedAt().plusMillis(1)));

        List<EventParticipantResponse> participants = eventService.getParticipants(event.id(), MEMBER_ID);

        assertEquals(CaptureStatus.ACCEPTED, capture.status());
        assertEquals(1, participants.size());
        assertEquals("viewer-001", participants.get(0).blazeUserId());
        assertEquals("Viewer One", participants.get(0).displayName());
        assertThrows(ForbiddenException.class,
                () -> eventService.getParticipants(event.id(), "other-member"));
    }

    @Test
    void finalizationIsIdempotentAndFreezesPoolMetadata() {
        EventResponse event = createDefaultEvent("Finalizar");
        EventResponse opened = eventService.openEvent(event.id(), MEMBER_ID);
        captureService.capture(new ChatEntryCandidate(
                CHANNEL_ID,
                "message-finalize-001",
                "!participar",
                "viewer-finalize-001",
                "viewer",
                "Viewer",
                "chat",
                opened.openedAt().plusMillis(1)));

        FinalizationStart start = eventService.beginFinalization(event.id(), MEMBER_ID);
        assertEquals("finalizing", start.event().status());
        assertNotNull(start.event().finalizationCutoffAt());
        EventResponse first = eventService.completeFinalization(event.id(), MEMBER_ID, start.attemptId());
        FinalizationStart repeatedStart = eventService.beginFinalization(event.id(), MEMBER_ID);
        EventResponse repeated = repeatedStart.event();

        assertEquals("closed", first.status());
        assertTrue(repeatedStart.alreadyFinalized());
        assertEquals(1, first.finalizedParticipantCount());
        assertEquals(64, first.finalizedPoolHash().length());
        assertEquals(first.closedAt(), repeated.closedAt());
        assertEquals(first.finalizedPoolHash(), repeated.finalizedPoolHash());
    }

    @Test
    void staleFinalizationAttemptCannotCompleteOrAbortANewerAttempt() {
        EventResponse event = createDefaultEvent("Finalizacao cercada");
        EventResponse opened = eventService.openEvent(event.id(), MEMBER_ID);
        captureService.capture(new ChatEntryCandidate(
                CHANNEL_ID,
                "message-fence-001",
                "!participar",
                "viewer-fence-001",
                "viewer-fence",
                "Viewer Fence",
                "chat",
                opened.openedAt().plusMillis(1)));

        FinalizationStart staleAttempt = eventService.beginFinalization(event.id(), MEMBER_ID);
        assertEquals(1, eventStore.recoverStaleFinalizations(
                Instant.now().plusSeconds(1),
                Instant.now()));
        FinalizationStart currentAttempt = eventService.beginFinalization(event.id(), MEMBER_ID);

        assertThrows(ConflictException.class, () -> eventService.completeFinalization(
                event.id(), MEMBER_ID, staleAttempt.attemptId()));
        eventService.abortFinalization(event.id(), MEMBER_ID, staleAttempt.attemptId());
        assertEquals("finalizing", eventService.getEvent(event.id()).status());

        EventResponse finalized = eventService.completeFinalization(
                event.id(), MEMBER_ID, currentAttempt.attemptId());
        assertEquals("closed", finalized.status());
        assertEquals(1, finalized.finalizedParticipantCount());
    }

    private EventResponse createDefaultEvent(String title) {
        return eventService.createEvent(request(title, "!participar"), MEMBER_ID, BLAZE_USER_ID, channel());
    }

    private EventResponse finishFinalization(String eventId) {
        FinalizationStart start = eventService.beginFinalization(eventId, MEMBER_ID);
        return eventService.completeFinalization(eventId, MEMBER_ID, start.attemptId());
    }

    private static CreateEventRequest request(String title, String entryCommand) {
        return new CreateEventRequest(
                title,
                "Use o comando no chat durante a live.",
                "Gift card de R$ 100",
                entryCommand,
                null,
                null,
                CHANNEL_SLUG);
    }

    private static BlazeChannelResponse channel() {
        return new BlazeChannelResponse(CHANNEL_ID, CHANNEL_SLUG, "Creator", null);
    }
}
