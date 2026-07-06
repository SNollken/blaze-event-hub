package com.blaze.eventhub.event;

import java.time.Instant;

public record EventRule(
        String id,
        String eventId,
        ActionType actionType,
        int thresholdAmount,
        int entries,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {
}
