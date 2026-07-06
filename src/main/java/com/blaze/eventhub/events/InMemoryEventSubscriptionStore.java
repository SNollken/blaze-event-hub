package com.blaze.eventhub.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryEventSubscriptionStore {

	private final ConcurrentHashMap<String, EventSubscriptionSnapshot> subscriptions = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<EventSubscriptionSnapshot> mapper = (rs, rowNum) -> new EventSubscriptionSnapshot(
			rs.getString("id"),
			BlazeEventType.valueOf(rs.getString("type")),
			rs.getString("version"),
			rs.getString("channel_id"),
			rs.getString("session_id"),
			rs.getTimestamp("created_at").toInstant());

	public InMemoryEventSubscriptionStore() {
		this.jdbc = null;
	}

	@Autowired
	public InMemoryEventSubscriptionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void save(EventSubscriptionSnapshot snapshot) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO event_subscriptions KEY(id)
					VALUES (?, ?, ?, ?, ?, ?)
					""",
					snapshot.id(),
					snapshot.type().name(),
					snapshot.version(),
					snapshot.channelId(),
					snapshot.sessionId(),
					snapshot.createdAt());
			return;
		}
		subscriptions.put(snapshot.id(), snapshot);
	}

	public List<EventSubscriptionSnapshot> list() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM event_subscriptions ORDER BY created_at", mapper);
		}
		return new ArrayList<>(subscriptions.values());
	}

	public void clear() {
		if (jdbc != null) {
			jdbc.update("DELETE FROM event_subscriptions");
			return;
		}
		subscriptions.clear();
	}
}
