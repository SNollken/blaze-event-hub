package com.blaze.eventhub.event.draw;

import java.time.Instant;

public record EventWinner(
        String id,
        String eventId,
        String memberId,
        int entriesAtDrawTime,
        String drawSeed,
        String drawMethod,
        Instant selectedAt,
        String selectedBy,
        String notes) {
}
