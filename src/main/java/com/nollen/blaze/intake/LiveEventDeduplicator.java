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

	// ponytail: LinkedHashMap LRU is read+write from the event intake path under
	// concurrency (REST create + WebSocket dispatch). Access is synchronized on the
	// map instance itself (DB call kept outside the lock) so the cache stays correct
	// without serializing the slow existsByDedupKey query.
	public boolean isDuplicate(String dedupKey) {
		if (dedupKey == null || dedupKey.isBlank()) {
			return false;
		}
		boolean known;
		synchronized (knownDuplicates) {
			known = Boolean.TRUE.equals(knownDuplicates.get(dedupKey));
		}
		if (known) {
			return true;
		}
		boolean duplicate = store.existsByDedupKey(dedupKey);
		if (duplicate) {
			synchronized (knownDuplicates) {
				knownDuplicates.put(dedupKey, Boolean.TRUE);
			}
		}
		return duplicate;
	}
}
