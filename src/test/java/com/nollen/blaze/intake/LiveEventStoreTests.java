package com.nollen.blaze.intake;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveEventStoreTests {

	private LiveEventStore store;

	@BeforeEach
	void setUp() {
		store = new LiveEventStore();
	}

	@Test
	void savesAndFindsEvent() {
		LiveEvent event = sampleEvent("id-1");
		store.save(event);

		Optional<LiveEvent> found = store.findById("id-1");
		assertTrue(found.isPresent());
		assertEquals("id-1", found.get().id());
	}

	@Test
	void returnsEmptyForMissingId() {
		assertTrue(store.findById("missing").isEmpty());
	}

	@Test
	void listAllReturnsNewestFirst() {
		store.save(new LiveEvent("a", LiveEventType.FOLLOW, LiveEventSource.MANUAL,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.ofEpochSecond(100), null));
		store.save(new LiveEvent("b", LiveEventType.SUBSCRIPTION, LiveEventSource.SIMULATED,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.ofEpochSecond(200), null));

		var all = store.listAll();
		assertEquals(2, all.size());
		assertEquals("b", all.get(0).id());
	}

	@Test
	void countByStatusWorks() {
		store.save(new LiveEvent("a", LiveEventType.TEST, LiveEventSource.INTERNAL_TEST,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.now(), null));
		store.save(new LiveEvent("b", LiveEventType.TEST, LiveEventSource.INTERNAL_TEST,
				LiveEventStatus.DUPLICATE, Map.of(), Instant.now(), null));

		assertEquals(1, store.countByStatus(LiveEventStatus.ACCEPTED));
		assertEquals(1, store.countByStatus(LiveEventStatus.DUPLICATE));
		assertEquals(0, store.countByStatus(LiveEventStatus.REJECTED));
	}

	@Test
	void findByTypeFiltersCorrectly() {
		store.save(new LiveEvent("a", LiveEventType.FOLLOW, LiveEventSource.MANUAL,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.now(), null));
		store.save(new LiveEvent("b", LiveEventType.SUBSCRIPTION, LiveEventSource.MANUAL,
				LiveEventStatus.ACCEPTED, Map.of(), Instant.now(), null));

		assertEquals(1, store.findByType(LiveEventType.FOLLOW).size());
		assertEquals(1, store.findByType(LiveEventType.SUBSCRIPTION).size());
	}

	@Test
	void clearRemovesAll() {
		store.save(sampleEvent("a"));
		store.save(sampleEvent("b"));
		assertEquals(2, store.count());
		store.clear();
		assertEquals(0, store.count());
	}

	private LiveEvent sampleEvent(String id) {
		return new LiveEvent(id, LiveEventType.TEST, LiveEventSource.INTERNAL_TEST,
				LiveEventStatus.ACCEPTED, Map.of("key", "value"), Instant.now(), null);
	}
}
