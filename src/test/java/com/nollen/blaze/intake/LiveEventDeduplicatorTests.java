package com.nollen.blaze.intake;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveEventDeduplicatorTests {

	private LiveEventStore store;
	private LiveEventDeduplicator deduplicator;

	@BeforeEach
	void setUp() {
		store = new LiveEventStore();
		deduplicator = new LiveEventDeduplicator(store);
	}

	@Test
	void detectsDuplicate() {
		store.save(new LiveEvent("a", LiveEventType.FOLLOW, LiveEventSource.MANUAL,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.now(), "dup-key-1"));
		assertTrue(deduplicator.isDuplicate("dup-key-1"));
	}

	@Test
	void nullDedupKeyIsNeverDuplicate() {
		store.save(new LiveEvent("a", LiveEventType.FOLLOW, LiveEventSource.MANUAL,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.now(), null));
		assertFalse(deduplicator.isDuplicate(null));
	}

	@Test
	void blankDedupKeyIsNeverDuplicate() {
		assertFalse(deduplicator.isDuplicate(""));
		assertFalse(deduplicator.isDuplicate("  "));
	}

	@Test
	void notDuplicateIfOnlyRejected() {
		store.save(new LiveEvent("a", LiveEventType.FOLLOW, LiveEventSource.MANUAL,
				LiveEventStatus.REJECTED, Map.of(), Instant.now(), "dup-key-2"));
		assertFalse(deduplicator.isDuplicate("dup-key-2"));
	}

	@Test
	void notDuplicateWhenEmptyStore() {
		assertFalse(deduplicator.isDuplicate("any-key"));
	}
}
