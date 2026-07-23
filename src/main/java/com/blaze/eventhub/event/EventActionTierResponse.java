package com.blaze.eventhub.event;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO para tier de ação.
 */
public record EventActionTierResponse(
        String id,
        String eventId,
        String actionType,
        int threshold,
        int entries,
        int tierOrder,
        Instant createdAt
) {
    public static List<EventActionTierResponse> fromList(List<EventActionTier> tiers) {
        return tiers.stream()
                .map(t -> new EventActionTierResponse(
                        t.id(), t.eventId(), t.actionType().value(),
                        t.threshold(), t.entries(), t.tierOrder(), t.createdAt()))
                .toList();
    }
}