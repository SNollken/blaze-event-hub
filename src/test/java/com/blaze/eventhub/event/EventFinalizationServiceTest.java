package com.blaze.eventhub.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.blaze.eventhub.events.BlazeChatPollingService;
import com.blaze.eventhub.events.PollingCycleResult;
import com.blaze.eventhub.common.ConflictException;
import com.blaze.eventhub.common.UpstreamUnavailableException;

class EventFinalizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");
    private static final String ATTEMPT_ID = "attempt-1";

    private EventService eventService;
    private BlazeChatPollingService pollingService;
    private EventStore eventStore;
    private EventFinalizationService finalizationService;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        pollingService = mock(BlazeChatPollingService.class);
        eventStore = mock(EventStore.class);
        finalizationService = new EventFinalizationService(
                eventService,
                pollingService,
                eventStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                120_000);
    }

    @Test
    void persistsCutoffThenSynchronizesAndFreezesPoolUnderChannelLock() {
        EventResponse started = mock(EventResponse.class);
        EventResponse expected = mock(EventResponse.class);
        when(eventService.beginFinalization("event-1", "member-1"))
                .thenReturn(new FinalizationStart(started, false, ATTEMPT_ID));
        when(eventService.completeFinalization("event-1", "member-1", ATTEMPT_ID)).thenReturn(expected);
        doAnswer(invocation -> {
            Function<PollingCycleResult, EventResponse> completion = invocation.getArgument(2);
            return completion.apply(new PollingCycleResult(1, 3, 2, 1, 0));
        }).when(pollingService).pollEventThen(eq("event-1"), eq("member-1"), any());

        EventResponse result = finalizationService.finalizeEvent("event-1", "member-1");

        assertEquals(expected, result);
        InOrder order = inOrder(eventService, pollingService);
        order.verify(eventService).beginFinalization("event-1", "member-1");
        order.verify(pollingService).pollEventThen(eq("event-1"), eq("member-1"), any());
        order.verify(eventService).completeFinalization("event-1", "member-1", ATTEMPT_ID);
    }

    @Test
    void reopensEventWhenFinalSynchronizationFails() {
        when(eventService.beginFinalization("event-1", "member-1"))
                .thenReturn(new FinalizationStart(mock(EventResponse.class), false, ATTEMPT_ID));
        doAnswer(invocation -> {
            Function<PollingCycleResult, EventResponse> completion = invocation.getArgument(2);
            return completion.apply(new PollingCycleResult(0, 0, 0, 0, 1));
        }).when(pollingService).pollEventThen(eq("event-1"), eq("member-1"), any());

        assertThrows(UpstreamUnavailableException.class,
                () -> finalizationService.finalizeEvent("event-1", "member-1"));

        verify(eventService, never()).completeFinalization("event-1", "member-1", ATTEMPT_ID);
        verify(eventService).abortFinalization("event-1", "member-1", ATTEMPT_ID);
    }

    @Test
    void reopensEventWhenTheFrozenPoolIsStillEmpty() {
        when(eventService.beginFinalization("event-1", "member-1"))
                .thenReturn(new FinalizationStart(mock(EventResponse.class), false, ATTEMPT_ID));
        when(eventService.completeFinalization("event-1", "member-1", ATTEMPT_ID))
                .thenThrow(new ConflictException("Nenhum participante entrou ainda."));
        doAnswer(invocation -> {
            Function<PollingCycleResult, EventResponse> completion = invocation.getArgument(2);
            return completion.apply(new PollingCycleResult(1, 0, 0, 0, 0));
        }).when(pollingService).pollEventThen(eq("event-1"), eq("member-1"), any());

        assertThrows(ConflictException.class,
                () -> finalizationService.finalizeEvent("event-1", "member-1"));

        verify(eventService).abortFinalization("event-1", "member-1", ATTEMPT_ID);
    }

    @Test
    void returnsPersistedEventWithoutPollingWhenAlreadyFinalized() {
        EventResponse expected = mock(EventResponse.class);
        when(eventService.beginFinalization("event-1", "member-1"))
                .thenReturn(new FinalizationStart(expected, true, null));

        assertEquals(expected, finalizationService.finalizeEvent("event-1", "member-1"));

        verify(pollingService, never()).pollEventThen(eq("event-1"), eq("member-1"), any());
    }

    @Test
    void recoversInterruptedFinalizationsAfterTimeout() {
        when(eventStore.recoverStaleFinalizations(NOW.minusMillis(120_000), NOW)).thenReturn(1);

        finalizationService.recoverInterruptedFinalizations();

        verify(eventStore).recoverStaleFinalizations(NOW.minusMillis(120_000), NOW);
    }
}
