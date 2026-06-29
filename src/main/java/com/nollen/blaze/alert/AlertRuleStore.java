package com.nollen.blaze.alert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.nollen.blaze.events.BlazeEventType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRuleStore {

	private final ConcurrentHashMap<String, AlertRule> rules = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<AlertRule> mapper = (rs, rowNum) -> new AlertRule(
			rs.getString("id"),
			rs.getString("name"),
			BlazeEventType.from(rs.getString("event_type")),
			AlertCondition.valueOf(rs.getString("rule_condition")),
			rs.getDouble("threshold"),
			rs.getString("template"),
			rs.getBoolean("enabled"),
			rs.getLong("cooldown_ms"));

	public AlertRuleStore() {
		this.jdbc = null;
	}

	@Autowired
	public AlertRuleStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public AlertRule save(AlertRule rule) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO alert_rules KEY(id)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""",
					rule.id(),
					rule.name(),
					rule.eventType().id(),
					rule.condition().name(),
					rule.threshold(),
					rule.template(),
					rule.enabled(),
					rule.cooldownMs());
			return rule;
		}
		rules.put(rule.id(), rule);
		return rule;
	}

	public Optional<AlertRule> findById(String id) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alert_rules WHERE id = ?", mapper, id).stream().findFirst();
		}
		return Optional.ofNullable(rules.get(id));
	}

	public List<AlertRule> findAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM alert_rules ORDER BY id", mapper);
		}
		return rules.values().stream()
				.sorted(Comparator.comparing(AlertRule::id))
				.toList();
	}

	public void delete(String id) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM alert_rules WHERE id = ?", id);
			return;
		}
		rules.remove(id);
	}

	public long count() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_rules", Long.class);
			return count == null ? 0 : count;
		}
		return rules.size();
	}
}
