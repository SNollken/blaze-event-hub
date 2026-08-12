package com.blaze.eventhub.alert;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import com.blaze.eventhub.common.JsonData;
import com.blaze.eventhub.events.BlazeEventType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AlertStore {

	static final int MAX_ALERTS = 1000;
	private static final Comparator<Alert> BY_TIME = Comparator.comparing(
			(Alert a) -> a.triggeredAt() != null ? a.triggeredAt() : Instant.EPOCH).reversed();

	private final ConcurrentLinkedDeque<Alert> alerts = new ConcurrentLinkedDeque<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<Alert> mapper = (rs, rowNum) -> new Alert(
			rs.getString("id"),
			rs.getString("rule_id"),
			rs.getString("rule_name"),
			BlazeEventType.from(rs.getString("event_type")),
			rs.getTimestamp("triggered_at") != null ? rs.getTimestamp("triggered_at").toInstant() : Instant.EPOCH,
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
			Timestamp triggeredAt = Timestamp.from(alert.triggeredAt());
			int updated = jdbc.update(
				"UPDATE alerts SET rule_id = ?, rule_name = ?, event_type = ?, triggered_at = ?, message = ?, acknowledged = ?, metadata = ? WHERE id = ?",
				alert.ruleId(), alert.ruleName(), alert.eventType().id(), triggeredAt, alert.message(), alert.acknowledged(), JsonData.write(alert.metadata()), alert.id());
			if (updated == 0) {
				jdbc.update(
					"INSERT INTO alerts (rule_id, rule_name, event_type, triggered_at, message, acknowledged, metadata, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
					alert.ruleId(), alert.ruleName(), alert.eventType().id(), triggeredAt, alert.message(), alert.acknowledged(), JsonData.write(alert.metadata()), alert.id());
			}

			return alert;
		}
		alerts.removeIf(a -> a.id().equals(alert.id()));
		alerts.addFirst(alert);
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
			return jdbc.query("SELECT * FROM alerts WHERE acknowledged = FALSE ORDER BY triggered_at DESC LIMIT ?", mapper, MAX_ALERTS);
		}
		return alerts.stream()
				.filter(a -> !a.acknowledged())
				.sorted(BY_TIME)
				.limit(MAX_ALERTS)
				.toList();
	}

	public List<Alert> findAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts ORDER BY triggered_at DESC LIMIT ?", mapper, MAX_ALERTS);
		}
		return alerts.stream()
				.sorted(BY_TIME)
				.limit(MAX_ALERTS)
				.toList();
	}

	public List<Alert> findByEventType(String eventTypeId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts WHERE event_type = ? ORDER BY triggered_at DESC LIMIT ?", mapper, eventTypeId, MAX_ALERTS);
		}
		return alerts.stream()
				.filter(a -> a.eventType() != null && a.eventType().id().equals(eventTypeId))
				.sorted(BY_TIME)
				.limit(MAX_ALERTS)
				.toList();
	}
	public Optional<Alert> findLastByRuleId(String ruleId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alerts WHERE rule_id = ? ORDER BY triggered_at DESC LIMIT 1", mapper, ruleId)
					.stream().findFirst();
		}
		return alerts.stream()
				.filter(a -> a.ruleId() != null && a.ruleId().equals(ruleId))
				.sorted(BY_TIME)
				.findFirst();
	}

	public Map<String, Optional<Alert>> findLastByRuleIds(List<String> ruleIds) {
		Map<String, Optional<Alert>> result = new HashMap<>();
		for (String id : ruleIds) {
			result.put(id, Optional.empty());
		}
		if (jdbc != null && !ruleIds.isEmpty()) {
			String placeholders = ruleIds.stream().map(r -> "?").collect(Collectors.joining(","));
			List<Alert> rows = jdbc.query(
					"SELECT * FROM alerts WHERE rule_id IN (" + placeholders + ") ORDER BY triggered_at DESC",
					mapper, ruleIds.toArray());
			for (Alert alert : rows) {
				if (alert.ruleId() != null && result.get(alert.ruleId()).isEmpty()) {
					result.put(alert.ruleId(), Optional.of(alert));
				}
			}
			return result;
		}
		for (String id : ruleIds) {
			result.put(id, alerts.stream()
					.filter(a -> id.equals(a.ruleId()))
					.sorted(BY_TIME)
					.findFirst());
		}
		return result;
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
