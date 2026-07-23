package com.blaze.eventhub.event;

import java.time.Instant;
import java.util.List;

public record EventActionTierResponse(
        String id,
        String eventId,
        String actionType,
        int threshold,
        int entries,
        int tierOrder,
        Instant createdAt) {

    public static EventActionTierResponse from(EventActionTier tier) {
        return new EventActionTierResponse(
                tier.id(),
                tier.eventId(),
                tier.actionType().value(),
                tier.threshold(),
                tier.entries(),
                tier.tierOrder(),
                tier.createdAt());
    }

    public static List<EventActionTierResponse> fromList(List<EventActionTier> tiers) {
        return tiers.stream().map(EventActionTierResponse::from).toList();
    }
}