package com.blaze.eventhub.event.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.common.ConflictException;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.participant.EventParticipant;
import com.blaze.eventhub.event.participant.EventParticipantStore;
import com.blaze.eventhub.event.participant.ParticipantPoolHasher;

class DrawServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    private EventWinnerStore winnerStore;
    private EventStore eventStore;
    private EventParticipantStore participantStore;
    private IdGenerator idGenerator;
    private RandomGenerator seedGenerator;
    private DrawService drawService;

    @BeforeEach
    void setUp() {
        winnerStore = mock(EventWinnerStore.class);
        eventStore = mock(EventStore.class);
        participantStore = mock(EventParticipantStore.class);
        idGenerator = mock(IdGenerator.class);
        seedGenerator = mock(RandomGenerator.class);
        when(idGenerator.newId()).thenReturn("result-1");
        when(eventStore.completeEvent("event-1", NOW)).thenReturn(1);

        drawService = new DrawService(
                winnerStore,
                eventStore,
                participantStore,
                idGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                seedGenerator);
    }

    @Test
    void executeDrawUsesFrozenBlazeParticipantsAndPersistsAuditData() {
        List<EventParticipant> participants = List.of(
                participant("blaze-b", "B"),
                participant("blaze-a", "A"));
        when(eventStore.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(closedEvent("creator-1", participants)));
        when(winnerStore.findByEventId("event-1")).thenReturn(Optional.empty());
        when(participantStore.findByEventId("event-1")).thenReturn(participants);
        when(seedGenerator.nextLong()).thenReturn(1L);

        EventWinner result = drawService.executeDraw("event-1", "creator-1");

        assertEquals("event-1", result.eventId());
        assertEquals(ParticipantPoolHasher.sha256(participants), result.poolHash());
        assertEquals(2, result.participantCount());
        assertEquals("1", result.drawSeed());
        assertEquals("uniform_blaze_participants_v1", result.drawMethod());
        verify(winnerStore).save(result);
        verify(eventStore).completeEvent("event-1", NOW);
    }

    @Test
    void executeDrawReturnsPersistedResultWhenEventIsAlreadyCompleted() {
        EventWinner existing = result("blaze-a");
        when(eventStore.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(completedEvent("creator-1")));
        when(winnerStore.findByEventId("event-1")).thenReturn(Optional.of(existing));

        EventWinner result = drawService.executeDraw("event-1", "creator-1");

        assertEquals(existing, result);
        verify(winnerStore, never()).save(any());
        verify(eventStore, never()).completeEvent(any(), any());
    }

    @Test
    void executeDrawRejectsEmptyPool() {
        List<EventParticipant> empty = List.of();
        when(eventStore.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(closedEvent("creator-1", empty)));
        when(winnerStore.findByEventId("event-1")).thenReturn(Optional.empty());
        when(participantStore.findByEventId("event-1")).thenReturn(empty);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> drawService.executeDraw("event-1", "creator-1"));

        assertEquals("Nenhum participante elegivel para sorteio.", error.getMessage());
        verify(seedGenerator, never()).nextLong();
    }

    @Test
    void executeDrawRejectsNonCreator() {
        when(eventStore.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(completedEvent("creator-1")));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> drawService.executeDraw("event-1", "intruder"));

        assertEquals("Somente o criador pode realizar esta acao.", error.getMessage());
        verify(winnerStore, never()).findByEventId(any());
    }

    private static EventParticipant participant(String blazeUserId, String displayName) {
        return new EventParticipant(
                "participant-" + blazeUserId,
                "event-1",
                blazeUserId,
                blazeUserId,
                displayName,
                "message-" + blazeUserId,
                "chat",
                1,
                NOW,
                NOW);
    }

    private static Event closedEvent(String creatorMemberId, List<EventParticipant> participants) {
        return event(creatorMemberId, EventStatus.CLOSED, participants.size(), ParticipantPoolHasher.sha256(participants));
    }

    private static Event completedEvent(String creatorMemberId) {
        return event(creatorMemberId, EventStatus.COMPLETED, 1, "hash");
    }

    private static Event event(String creatorMemberId, EventStatus status, int participantCount, String poolHash) {
        return new Event(
                "event-1",
                creatorMemberId,
                "blaze-user-1",
                "channel-1",
                "creator",
                "Creator",
                null,
                "Evento",
                "Descricao",
                null,
                "Premio",
                "!participar",
                status,
                participantCount,
                poolHash,
                null,
                null,
                NOW,
                NOW,
                status == EventStatus.CLOSED || status == EventStatus.COMPLETED ? NOW : null,
                null,
                null,
                status == EventStatus.CLOSED ? NOW : null,
                status == EventStatus.COMPLETED ? NOW : null);
    }

    private static EventWinner result(String blazeUserId) {
        return new EventWinner(
                "result-1",
                "event-1",
                blazeUserId,
                blazeUserId,
                blazeUserId,
                "1",
                "uniform_blaze_participants_v1",
                "hash",
                1,
                NOW,
                "creator-1");
    }
}
