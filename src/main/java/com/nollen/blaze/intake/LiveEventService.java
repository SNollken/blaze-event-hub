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
import com.nollen.blaze.points.LiveEventCreatedEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LiveEventService {

	private final LiveEventStore store;
	private final LiveEventNormalizer normalizer;
	private final LiveEventDeduplicator deduplicator;
	private final PayloadSanitizer sanitizer;
	private final IdGenerator idGenerator;
	private final Clock clock;
	private final ApplicationEventPublisher eventPublisher;

	public LiveEventService(LiveEventStore store, LiveEventNormalizer normalizer,
			LiveEventDeduplicator deduplicator, PayloadSanitizer sanitizer,
			IdGenerator idGenerator, Clock clock, ApplicationEventPublisher eventPublisher) {
		this.store = store;
		this.normalizer = normalizer;
		this.deduplicator = deduplicator;
		this.sanitizer = sanitizer;
		this.idGenerator = idGenerator;
		this.clock = clock;
		this.eventPublisher = eventPublisher;
	}

	public List<LiveEvent> listAll() {
		return store.listAll();
	}

	public List<LiveEvent> listFiltered(LiveEventType type, LiveEventSource source, LiveEventStatus status) {
		if (type != null && source != null) {
			return store.findByTypeAndSource(type, source);
		}
		if (type != null) {
			return store.findByType(type);
		}
		if (source != null) {
			return store.findBySource(source);
		}
		if (status != null) {
			return store.findByStatus(status);
		}
		return store.listAll();
	}

	public LiveEvent getById(String id) {
		return store.findById(id)
				.orElseThrow(() -> new NotFoundException("LiveEvent not found: " + id));
	}

	public LiveEventStats stats() {
		return new LiveEventStats(
				store.count(),
				store.countByStatus(LiveEventStatus.ACCEPTED),
				store.countByStatus(LiveEventStatus.DUPLICATE),
				store.countByStatus(LiveEventStatus.REJECTED),
				store.countByStatus(LiveEventStatus.NORMALIZED),
				store.countByStatus(LiveEventStatus.DISPATCH_PENDING),
				store.countByStatus(LiveEventStatus.DISPATCHED_PLACEHOLDER),
				store.countByStatus(LiveEventStatus.FAILED));
	}

	public LiveEvent create(LiveEventType type, LiveEventSource source, Map<String, Object> payload, String dedupKey) {
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
		LiveEvent saved = store.save(event);
		
		// Publicar evento para Points Economy
		eventPublisher.publishEvent(new LiveEventCreatedEvent(saved));
		
		return saved;
	}

	public LiveEvent simulate() {
		Map<String, Object> payload = new HashMap<>();
		payload.put("username", "SimUser" + (int) (Math.random() * 1000));
		payload.put("message", "Hello from simulation!");
		payload.put("amount", Math.round(Math.random() * 100) / 10.0);
		return create(LiveEventType.TEST, LiveEventSource.SIMULATED, payload, null);
	}
}
