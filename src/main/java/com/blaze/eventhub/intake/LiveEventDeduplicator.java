package com.blaze.eventhub.intake;

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

	// ponytail: cache check + DB query are now atomic (was TOCTOU: cache miss then DB read
	// outside lock allowed concurrent duplicates). The DB query is a fast indexed COUNT;
	// if it ever becomes slow, escalate to a UNIQUE constraint on dedup_key in the store.
	public boolean isDuplicate(String dedupKey) {
		if (dedupKey == null || dedupKey.isBlank()) {
			return false;
		}
		synchronized (knownDuplicates) {
			boolean known = Boolean.TRUE.equals(knownDuplicates.get(dedupKey));
			if (known) {
				return true;
			}
			boolean duplicate = store.existsByDedupKey(dedupKey);
			if (duplicate) {
				knownDuplicates.put(dedupKey, Boolean.TRUE);
			}
			return duplicate;
		}
	}
}
