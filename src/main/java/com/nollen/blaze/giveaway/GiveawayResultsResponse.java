package com.nollen.blaze.giveaway;

import java.time.Instant;
import java.util.List;

public record GiveawayResultsResponse(
		String giveawayId,
		String title,
		GiveawayStatus status,
		int totalEntries,
		int winnerCount,
		List<WinnerEntry> winners,
		Instant drawnAt) {

	public record WinnerEntry(
			String entryId,
			String participantName,
			Instant enteredAt) {
	}

	public static GiveawayResultsResponse from(Giveaway giveaway, List<GiveawayEntry> entries) {
		List<GiveawayEntry> winners = entries.stream()
				.filter(GiveawayEntry::selected)
				.toList();
		List<WinnerEntry> winnerEntries = winners.stream()
				.map(w -> new WinnerEntry(w.id(), w.participantName(), w.enteredAt()))
				.toList();
		return new GiveawayResultsResponse(
				giveaway.id(),
				giveaway.title(),
				giveaway.status(),
				entries.size(),
				winners.size(),
				winnerEntries,
				giveaway.drawnAt());
	}
}
