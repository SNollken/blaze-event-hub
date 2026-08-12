package com.blaze.eventhub.intake;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.blaze.eventhub.common.IdGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

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
	void getListFilteredByStatus() {
		service.create(LiveEventType.FOLLOW, LiveEventSource.MANUAL, Map.of(), null);
		service.create(LiveEventType.SUBSCRIPTION, LiveEventSource.MANUAL, Map.of(), null);
		service.create(LiveEventType.SUBSCRIPTION, LiveEventSource.SIMULATED, Map.of(), "dup-status");
		// third call is a duplicate
		service.create(LiveEventType.SUBSCRIPTION, LiveEventSource.SIMULATED, Map.of(), "dup-status");

		assertEquals(3, service.listFiltered(null, null, LiveEventStatus.ACCEPTED).size());
		assertEquals(1, service.listFiltered(null, null, LiveEventStatus.DUPLICATE).size());
	}

	@Test
	void getListFilteredByTypeAndSource() {
		service.create(LiveEventType.FOLLOW, LiveEventSource.MANUAL, Map.of(), null);
		service.create(LiveEventType.FOLLOW, LiveEventSource.SIMULATED, Map.of(), null);
		service.create(LiveEventType.SUBSCRIPTION, LiveEventSource.MANUAL, Map.of(), null);

		assertEquals(1, service.listFiltered(LiveEventType.FOLLOW, LiveEventSource.MANUAL, null).size());
		assertEquals(0, service.listFiltered(LiveEventType.SUBSCRIPTION, LiveEventSource.SIMULATED, null).size());
	}

	@Test
	void getByIdThrowsForMissing() {
		assertThrows(com.blaze.eventhub.common.NotFoundException.class,
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

	@Test
	void concurrentCreateWithSameDedupKeyProducesOneAcceptedOneDuplicate() throws Exception {
		// ponytail: slows existsByDedupKey to widen the TOCTOU race window.
		// With synchronized create(), only one thread can be in the check-then-save
		// critical section at a time, so the second sees the first's saved event.
		LiveEventStore slowStore = new LiveEventStore() {
			@Override
			public boolean existsByDedupKey(String dedupKey) {
				try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				return super.existsByDedupKey(dedupKey);
			}
		};
		LiveEventService slowService = new LiveEventService(
				slowStore, new LiveEventNormalizer(), new LiveEventDeduplicator(slowStore),
				new PayloadSanitizer(), new IdGenerator(),
				Clock.fixed(Instant.ofEpochSecond(1_000_000), ZoneOffset.UTC));

		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.List<LiveEvent> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

		java.util.concurrent.Callable<LiveEvent> task = () -> {
			start.await();
			return slowService.create(LiveEventType.FOLLOW, LiveEventSource.MANUAL, Map.of(), "race-key");
		};

		java.util.concurrent.Future<LiveEvent> f1 = executor.submit(task);
		java.util.concurrent.Future<LiveEvent> f2 = executor.submit(task);
		start.countDown();
		results.add(f1.get());
		results.add(f2.get());
		executor.shutdown();

		// One ACCEPTED (first thread), one DUPLICATE (second sees first's save).
		assertThat(results).hasSize(2);
		assertThat(results.stream().map(e -> e.status()).toList())
				.containsExactlyInAnyOrder(LiveEventStatus.ACCEPTED, LiveEventStatus.DUPLICATE);
	}
}
