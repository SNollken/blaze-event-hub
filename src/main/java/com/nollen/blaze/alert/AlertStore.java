package com.nollen.blaze.alert;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

@Repository
public class AlertStore {

	private static final int MAX_ALERTS = 1000;
	private static final Comparator<Alert> BY_TIME = Comparator.comparing(
			(Alert a) -> a.triggeredAt() != null ? a.triggeredAt() : Instant.EPOCH).reversed();

	private final ConcurrentLinkedDeque<Alert> alerts = new ConcurrentLinkedDeque<>();
	private final AtomicInteger counter = new AtomicInteger(0);

	public Alert save(Alert alert) {
		alerts.removeIf(a -> a.id().equals(alert.id()));
		alerts.addFirst(alert);
		counter.incrementAndGet();
		while (alerts.size() > MAX_ALERTS) {
			alerts.removeLast();
		}
		return alert;
	}

	public Optional<Alert> findById(String id) {
		return alerts.stream().filter(a -> a.id().equals(id)).findFirst();
	}

	public List<Alert> findActive() {
		return alerts.stream()
				.filter(a -> !a.acknowledged())
				.sorted(BY_TIME)
				.toList();
	}

	public List<Alert> findAll() {
		return alerts.stream()
				.sorted(BY_TIME)
				.toList();
	}

	public List<Alert> findByEventType(String eventTypeId) {
		return alerts.stream()
				.filter(a -> a.eventType() != null && a.eventType().id().equals(eventTypeId))
				.sorted(BY_TIME)
				.toList();
	}

	public long count() {
		return alerts.size();
	}

	public long countUnacknowledged() {
		return alerts.stream().filter(a -> !a.acknowledged()).count();
	}
}
