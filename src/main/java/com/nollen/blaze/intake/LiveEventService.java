package com.nollen.blaze.intake;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.springframework.stereotype.Service;

@Service
public class LiveEventService {

	private final LiveEventStore store;
	private final LiveEventNormalizer normalizer;
	private final LiveEventDeduplicator deduplicator;
	private final PayloadSanitizer sanitizer;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public LiveEventService(LiveEventStore store, LiveEventNormalizer normalizer,
			LiveEventDeduplicator deduplicator, PayloadSanitizer sanitizer,
			IdGenerator idGenerator, Clock clock) {
		this.store = store;
		this.normalizer = normalizer;
		this.deduplicator = deduplicator;
		this.sanitizer = sanitizer;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public List<LiveEvent> listAll() {
		return store.listAll();
	}

	public List<LiveEvent> listFiltered(LiveEventType type, LiveEventSource source, LiveEventStatus status) {
		List<LiveEvent> results;
		if (type != null && source != null) {
			results = store.findByTypeAndSource(type, source);
		} else if (type != null) {
			results = store.findByType(type);
		} else if (source != null) {
			results = store.findBySource(source);
		} else if (status != null) {
			results = store.findByStatus(status);
		} else {
			results = store.listAll();
		}
		return results.stream()
				.filter(e -> type == null || e.type() == type)
				.filter(e -> source == null || e.source() == source)
				.filter(e -> status == null || e.status() == status)
				.toList();
	}

	public LiveEvent getById(String id) {
		return store.findById(id)
				.orElseThrow(() -> new NotFoundException("LiveEvent not found: " + id));
	}

	public LiveEventStats stats() {
		Map<LiveEventStatus, Long> counts = store.countByStatusGrouped();
		return new LiveEventStats(
				store.count(),
				counts.getOrDefault(LiveEventStatus.ACCEPTED, 0L),
				counts.getOrDefault(LiveEventStatus.DUPLICATE, 0L),
				counts.getOrDefault(LiveEventStatus.REJECTED, 0L),
				counts.getOrDefault(LiveEventStatus.NORMALIZED, 0L),
				counts.getOrDefault(LiveEventStatus.DISPATCH_PENDING, 0L),
				counts.getOrDefault(LiveEventStatus.DISPATCHED_PLACEHOLDER, 0L),
				counts.getOrDefault(LiveEventStatus.FAILED, 0L));
	}

	// ponytail: synchronized to close the TOCTOU window between isDuplicate() and store.save().
	// Without it, concurrent calls with the same dedupKey both see "not duplicate" and both save.
	// Coarse-grained (like GiveawayService.enterGiveaway) — acceptable for the event intake path.
	// Upgrade path: DB-level UNIQUE constraint on dedup_key when switching to real concurrency.
	public synchronized LiveEvent create(LiveEventType type, LiveEventSource source, Map<String, Object> payload, String dedupKey) {
		Instant now = Instant.now(clock);
		String id = idGenerator.newId();
		String effectiveDedupKey = dedupKey != null && !dedupKey.isBlank() ? dedupKey : null;

		// Sanitize payload
		Map<String, Object> sanitizedPayload = sanitizer.sanitize(payload);
		if (sanitizer.isOversize(payload)) {
			LiveEvent rejected = new LiveEvent(id, type, source, LiveEventStatus.REJECTED,
					sanitizedPayload, now, effectiveDedupKey);
			return store.save(rejected);
		}

		// Dedup check
		if (effectiveDedupKey != null && deduplicator.isDuplicate(effectiveDedupKey)) {
			LiveEvent duplicate = new LiveEvent(id, type, source, LiveEventStatus.DUPLICATE,
					sanitizedPayload, now, effectiveDedupKey);
			return store.save(duplicate);
		}

		// Normalize
		Map<String, Object> normalizedPayload = normalizer.normalize(type, source, sanitizedPayload);

		LiveEvent event = new LiveEvent(id, type, source, LiveEventStatus.ACCEPTED,
				normalizedPayload, now, effectiveDedupKey);
		return store.save(event);
	}

	public LiveEvent simulate() {
		Map<String, Object> payload = new HashMap<>();
		payload.put("username", "SimUser" + (int) (Math.random() * 1000));
		payload.put("message", "Hello from simulation!");
		payload.put("amount", Math.round(Math.random() * 100) / 10.0);
		return create(LiveEventType.TEST, LiveEventSource.SIMULATED, payload, null);
	}
}
