package com.blaze.eventhub.event.participant;

import java.time.Instant;

public record EventParticipant(
        String id,
        String eventId,
        String blazeUserId,
        String blazeUsername,
        String displayName,
        String sourceMessageId,
        String actionType,
        int entryWeight,
        Instant enteredAt,
        Instant createdAt) {
}
