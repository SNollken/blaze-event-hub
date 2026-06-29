package com.nollen.blaze.intake;

import org.springframework.stereotype.Component;

@Component
public class LiveEventDeduplicator {

	private final LiveEventStore store;

	public LiveEventDeduplicator(LiveEventStore store) {
		this.store = store;
	}

	public boolean isDuplicate(String dedupKey) {
		if (dedupKey == null || dedupKey.isBlank()) {
			return false;
		}
		return store.listAll().stream()
				.filter(e -> dedupKey.equals(e.dedupKey()))
				.filter(e -> e.status() != LiveEventStatus.REJECTED)
				.findFirst()
				.isPresent();
	}
}
