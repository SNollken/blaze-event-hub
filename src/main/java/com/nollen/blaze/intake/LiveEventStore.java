package com.nollen.blaze.intake;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class LiveEventStore {

	private final ConcurrentHashMap<String, LiveEvent> events = new ConcurrentHashMap<>();

	public LiveEvent save(LiveEvent event) {
		events.put(event.id(), event);
		return event;
	}

	public Optional<LiveEvent> findById(String id) {
		return Optional.ofNullable(events.get(id));
	}

	public List<LiveEvent> listAll() {
		return events.values().stream()
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findByType(LiveEventType type) {
		return events.values().stream()
				.filter(e -> e.type() == type)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findBySource(LiveEventSource source) {
		return events.values().stream()
				.filter(e -> e.source() == source)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findByStatus(LiveEventStatus status) {
		return events.values().stream()
				.filter(e -> e.status() == status)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findByTypeAndSource(LiveEventType type, LiveEventSource source) {
		return events.values().stream()
				.filter(e -> e.type() == type && e.source() == source)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public long count() {
		return events.size();
	}

	public long countByStatus(LiveEventStatus status) {
		return events.values().stream().filter(e -> e.status() == status).count();
	}

	public void clear() {
		events.clear();
	}
}
