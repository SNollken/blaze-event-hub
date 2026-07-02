package com.nollen.blaze.points;

public record PointsBalance(
        String userId,
        String username,
        int totalPoints,
        int transactionCount
) {
}
