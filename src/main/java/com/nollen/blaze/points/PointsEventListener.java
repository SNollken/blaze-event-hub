package com.nollen.blaze.points;

import com.nollen.blaze.intake.LiveEventStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PointsEventListener {

    private static final Logger log = LoggerFactory.getLogger(PointsEventListener.class);

    private final PointsService pointsService;

    public PointsEventListener(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @EventListener
    public void onLiveEventCreated(LiveEventCreatedEvent event) {
        if (event.liveEvent().status() != LiveEventStatus.ACCEPTED) {
            log.debug("Ignoring non-ACCEPTED event: {}", event.liveEvent().id());
            return;
        }

        pointsService.creditFromEvent(event.liveEvent())
                .ifPresent(ledger ->
                        log.info("Points credited: {} points to '{}' for event {} (type: {})",
                                ledger.points(),
                                ledger.username(),
                                ledger.sourceEventId(),
                                ledger.eventType()));
    }
}
