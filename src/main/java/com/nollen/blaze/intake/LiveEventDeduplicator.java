package com.nollen.blaze.intake;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LiveEventDeduplicator {

	private final LiveEventStore store;
	private final Map<String, Boolean> knownDuplicates = new LinkedHashMap<>(100, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
			return size() > 10_000;
		}
	};

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
		boolean duplicate = store.existsByDedupKey(dedupKey);
		if (duplicate) {
			knownDuplicates.put(dedupKey, Boolean.TRUE);
		}
		return duplicate;
	}
}
