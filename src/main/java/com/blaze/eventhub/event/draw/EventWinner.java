package com.blaze.eventhub.event.draw;

import java.time.Instant;

public record EventWinner(
        String id,
        String eventId,
        String winnerBlazeUserId,
        String winnerUsername,
        String winnerDisplayName,
        String drawSeed,
        String drawMethod,
        String poolHash,
        int participantCount,
        Instant selectedAt,
        String selectedBy) {
}
