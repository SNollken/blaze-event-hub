package com.blaze.eventhub.event.participant;

import java.time.Instant;

public record EventParticipant(
        String id,
        String eventId,
        String blazeUserId,
        String blazeUsername,
        String displayName,
        String sourceMessageId,
        Instant enteredAt,
        Instant createdAt) {
}
