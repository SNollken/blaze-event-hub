package com.nollen.blaze.intake;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.nollen.blaze.common.IdGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveEventServiceTests {

	private LiveEventService service;
	private LiveEventStore store;

	@BeforeEach
	void setUp() {
		store = new LiveEventStore();
		LiveEventNormalizer normalizer = new LiveEventNormalizer();
		LiveEventDeduplicator deduplicator = new LiveEventDeduplicator(store);
		PayloadSanitizer sanitizer = new PayloadSanitizer();
		IdGenerator idGenerator = new IdGenerator();
		Clock clock = Clock.fixed(Instant.ofEpochSecond(1_000_000), ZoneOffset.UTC);
		service = new LiveEventService(store, normalizer, deduplicator, sanitizer, idGenerator, clock);
	}

	@Test
	void createEventWithAcceptedStatus() {
		Map<String, Object> payload = Map.of("message", "Hello");
		LiveEvent event = service.create(LiveEventType.CHAT_MESSAGE, LiveEventSource.MANUAL, payload, null);

		assertNotNull(event.id());
		assertEquals(LiveEventStatus.ACCEPTED, event.status());
		assertEquals(LiveEventType.CHAT_MESSAGE, event.type());
		assertEquals(LiveEventSource.MANUAL, event.source());
	}

	@Test
	void duplicateDetectionMarksAsDuplicate() {
		Map<String, Object> payload = Map.of("key", "val");
		service.create(LiveEventType.FOLLOW, LiveEventSource.MANUAL, payload, "dedup-1");
		LiveEvent second = service.create(LiveEventType.FOLLOW, LiveEventSource.MANUAL, payload, "dedup-1");

		assertEquals(LiveEventStatus.DUPLICATE, second.status());
	}

	@Test
	void getListFilteredByType() {
		service.create(LiveEventType.FOLLOW, LiveEventSource.MANUAL, Map.of(), null);
		service.create(LiveEventType.SUBSCRIPTION, LiveEventSource.MANUAL, Map.of(), null);

		assertEquals(1, service.listFiltered(LiveEventType.FOLLOW, null, null).size());
	}

	@Test
	void getByIdThrowsForMissing() {
		assertThrows(com.nollen.blaze.common.NotFoundException.class,
				() -> service.getById("nonexistent"));
	}

	@Test
	void statsReturnCorrectCounts() {
		service.create(LiveEventType.TEST, LiveEventSource.SIMULATED, Map.of(), null);
		service.create(LiveEventType.TEST, LiveEventSource.SIMULATED, Map.of(), null);
		service.create(LiveEventType.TEST, LiveEventSource.SIMULATED, Map.of(), "dup-stats");

		// The third call will be a duplicate
		service.create(LiveEventType.TEST, LiveEventSource.SIMULATED, Map.of(), "dup-stats");

		LiveEventStats stats = service.stats();
		assertEquals(4, stats.totalEvents());
		assertEquals(3, stats.acceptedCount());
		assertEquals(1, stats.duplicateCount());
	}

	@Test
	void simulateCreatesTestEvent() {
		LiveEvent event = service.simulate();
		assertNotNull(event.id());
		assertEquals(LiveEventType.TEST, event.type());
		assertEquals(LiveEventSource.SIMULATED, event.source());
		assertEquals(LiveEventStatus.ACCEPTED, event.status());
	}

	@Test
	void normalizeStripsXss() {
		Map<String, Object> payload = Map.of("message", "Hello <script>alert('x')</script> world");
		LiveEvent event = service.create(LiveEventType.CHAT_MESSAGE, LiveEventSource.MANUAL, payload, null);

		assertFalse(event.payload().get("message").toString().contains("<script>"));
	}
}
