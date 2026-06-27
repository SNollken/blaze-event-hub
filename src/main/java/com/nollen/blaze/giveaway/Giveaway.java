package com.nollen.blaze.giveaway;

import java.time.Instant;
import java.util.List;

public record Giveaway(
		String id,
		String title,
		String description,
		GiveawayStatus status,
		int entryCount,
		int maxEntries,
		Instant createdAt,
		Instant openedAt,
		Instant closedAt,
		Instant drawnAt,
		List<String> winnerIds) {

	public Giveaway {
		if (winnerIds == null) {
			winnerIds = List.of();
		}
		else {
			winnerIds = List.copyOf(winnerIds);
		}
	}
}
