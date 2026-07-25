package com.nollen.blaze.intake;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class LiveEventDeduplicator {

	private final LiveEventStore store;
	private final ConcurrentHashMap<String, Boolean> knownDuplicates = new ConcurrentHashMap<>();

	public LiveEventDeduplicator(LiveEventStore store) {
		this.store = store;
	}

	public boolean isDuplicate(String dedupKey) {
		if (dedupKey == null || dedupKey.isBlank()) {
			return false;
		}
		if (Boolean.TRUE.equals(knownDuplicates.get(dedupKey))) {
			return true;
		}
		boolean duplicate = store.listAll().stream()
				.filter(e -> dedupKey.equals(e.dedupKey()))
				.filter(e -> e.status() != LiveEventStatus.REJECTED)
				.findFirst()
				.isPresent();
		if (duplicate) {
			knownDuplicates.put(dedupKey, Boolean.TRUE);
		}
		return duplicate;
	}
}
