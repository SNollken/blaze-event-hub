package com.blaze.eventhub.event;

import java.time.Instant;

public record EventRuleResponse(
        String id,
        String eventId,
        String actionType,
        int thresholdAmount,
        int entries,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {

    public static EventRuleResponse from(EventRule rule) {
        return new EventRuleResponse(
                rule.id(),
                rule.eventId(),
                rule.actionType().name().toLowerCase(),
                rule.thresholdAmount(),
                rule.entries(),
                rule.isActive(),
                rule.createdAt(),
                rule.updatedAt());
    }
}
