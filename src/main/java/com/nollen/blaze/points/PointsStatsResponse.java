package com.nollen.blaze.points;

import java.util.List;

public record PointsStatsResponse(
        int totalUsers,
        int totalPoints,
        int totalTransactions,
        List<PointsRule> rules
) {
}
