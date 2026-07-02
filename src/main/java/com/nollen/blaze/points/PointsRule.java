package com.nollen.blaze.points;

public record PointsRule(
        String id,
        String eventType,
        int pointsPerEvent,
        String description,
        boolean enabled
) {
}
