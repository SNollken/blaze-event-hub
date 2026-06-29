package com.nollen.blaze.alert;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.nollen.blaze.common.JsonData;
import com.nollen.blaze.events.BlazeEventType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AlertStore {

	private static final int MAX_ALERTS = 1000;
	private static final Comparator<Alert> BY_TIME = Comparator.comparing(
			(Alert a) -> a.triggeredAt() != null ? a.triggeredAt() : Instant.EPOCH).reversed();

	private final ConcurrentLinkedDeque<Alert> alerts = new ConcurrentLinkedDeque<>();
	private final AtomicInteger counter = new AtomicInteger(0);
	private final JdbcTemplate jdbc;
	private final RowMapper<Alert> mapper = (rs, rowNum) -> new Alert(
			rs.getString("id"),
			rs.getString("rule_id"),
			rs.getString("rule_name"),
			BlazeEventType.from(rs.getString("event_type")),
			rs.getTimestamp("triggered_at").toInstant(),
			rs.getString("message"),
			rs.getBoolean("acknowledged"),
			JsonData.readMap(rs.getString("metadata")));

	public AlertStore() {
		this.jdbc = null;
	}

	@Autowired
	public AlertStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Alert save(Alert alert) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO alerts KEY(id)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""",
					alert.id(),
					alert.ruleId(),
					alert.ruleName(),
					alert.eventType().id(),
					alert.triggeredAt(),
					alert.message(),
					alert.acknowledged(),
					JsonData.write(alert.metadata()));
			return alert;
		}
		alerts.removeIf(a -> a.id().equals(alert.id()));
		alerts.addFirst(alert);
		counter.incrementAndGet();
		while (alerts.size() > MAX_ALERTS) {
			alerts.removeLast();
		}
		return alert;
	}

	public Optional<Alert> findById(String id) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts WHERE id = ?", mapper, id).stream().findFirst();
		}
		return alerts.stream().filter(a -> a.id().equals(id)).findFirst();
	}

	public List<Alert> findActive() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts WHERE acknowledged = FALSE ORDER BY triggered_at DESC", mapper);
		}
		return alerts.stream()
				.filter(a -> !a.acknowledged())
				.sorted(BY_TIME)
				.toList();
	}

	public List<Alert> findAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts ORDER BY triggered_at DESC", mapper);
		}
		return alerts.stream()
				.sorted(BY_TIME)
				.toList();
	}

	public List<Alert> findByEventType(String eventTypeId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts WHERE event_type = ? ORDER BY triggered_at DESC", mapper, eventTypeId);
		}
		return alerts.stream()
				.filter(a -> a.eventType() != null && a.eventType().id().equals(eventTypeId))
				.sorted(BY_TIME)
				.toList();
	}

	public long count() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alerts", Long.class);
			return count == null ? 0 : count;
		}
		return alerts.size();
	}

	public long countUnacknowledged() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alerts WHERE acknowledged = FALSE", Long.class);
			return count == null ? 0 : count;
		}
		return alerts.stream().filter(a -> !a.acknowledged()).count();
	}
}
