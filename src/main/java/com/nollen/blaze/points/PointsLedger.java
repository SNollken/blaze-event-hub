package com.nollen.blaze.points;

import java.time.Instant;

public record PointsLedger(
        String id,
        String userId,
        String username,
        int points,
        String eventType,
        String sourceEventId,
        Instant timestamp
) {
}
