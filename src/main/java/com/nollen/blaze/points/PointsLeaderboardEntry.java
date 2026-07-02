package com.nollen.blaze.points;

public record PointsLeaderboardEntry(
        int rank,
        String userId,
        String username,
        int totalPoints
) {
}
