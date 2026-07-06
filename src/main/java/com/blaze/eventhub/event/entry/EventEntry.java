package com.blaze.eventhub.event.entry;

import java.time.Instant;

public record EventEntry(
        String id,
        String eventId,
        String memberId,
        String detectedActionId,
        String actionType,
        int amount,
        int entriesGranted,
        String calculationReason,
        Instant createdAt) {
}
