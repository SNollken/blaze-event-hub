package com.blaze.eventhub.event.participant;

import java.time.Instant;

public record ChatEntryCandidate(
        String channelId,
        String messageId,
        String message,
        String blazeUserId,
        String blazeUsername,
        String displayName,
        String actionType,
        Instant sentAt) {
}
