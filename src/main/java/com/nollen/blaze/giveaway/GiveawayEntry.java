package com.nollen.blaze.giveaway;

import java.time.Instant;

public record GiveawayEntry(
		String id,
		String giveawayId,
		String participantName,
		Instant enteredAt,
		boolean selected,
		boolean eligible) {
}
