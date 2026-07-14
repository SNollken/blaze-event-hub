package com.blaze.eventhub.event;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.events.BlazeChatPollingService;
import com.blaze.eventhub.events.PollingCycleResult;
import com.blaze.eventhub.common.UpstreamUnavailableException;

@Service
public class EventFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(EventFinalizationService.class);

    private final EventService eventService;
    private final BlazeChatPollingService pollingService;
    private final EventStore eventStore;
    private final Clock clock;
    private final long staleFinalizationMillis;

    public EventFinalizationService(
            EventService eventService,
            BlazeChatPollingService pollingService,
            EventStore eventStore,
            Clock clock,
            @Value("${eventhub.blaze.stale-finalization-ms:300000}") long staleFinalizationMillis) {
        this.eventService = eventService;
        this.pollingService = pollingService;
        this.eventStore = eventStore;
        this.clock = clock;
        this.staleFinalizationMillis = staleFinalizationMillis;
    }

    public EventResponse finalizeEvent(String eventId, String creatorMemberId) {
        FinalizationStart start = eventService.beginFinalization(eventId, creatorMemberId);
        if (start.alreadyFinalized()) {
            return start.event();
        }

        try {
            return pollingService.pollEventThen(eventId, creatorMemberId, synchronization -> {
                assertSuccessfulSynchronization(synchronization);
                return eventService.completeFinalization(eventId, creatorMemberId, start.attemptId());
            });
        } catch (RuntimeException failure) {
            try {
                eventService.abortFinalization(eventId, creatorMemberId, start.attemptId());
            } catch (RuntimeException abortFailure) {
                failure.addSuppressed(abortFailure);
            }
            throw failure;
        }
    }

    @Scheduled(fixedDelayString = "${eventhub.blaze.finalization-recovery-interval-ms:60000}")
    public void recoverInterruptedFinalizations() {
        Instant now = Instant.now(clock);
        int recovered = eventStore.recoverStaleFinalizations(
                now.minusMillis(staleFinalizationMillis),
                now);
        if (recovered > 0) {
            log.warn("Finalizacoes interrompidas reabertas automaticamente: count={}", recovered);
        }
    }

    private static void assertSuccessfulSynchronization(PollingCycleResult synchronization) {
        if (synchronization.failures() > 0) {
            throw new UpstreamUnavailableException(
                    "Nao foi possivel sincronizar o chat da Blaze. O evento foi reaberto; tente novamente.");
        }
    }
}
