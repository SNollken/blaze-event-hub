package com.nollen.blaze.giveaway;

import java.util.Map;

public record GiveawayStatsResponse(
		int totalGiveaways,
		int draftCount,
		int openCount,
		int closedCount,
		int completedCount,
		int cancelledCount,
		int totalEntries,
		Map<String, Integer> entriesPerGiveaway) {
}
