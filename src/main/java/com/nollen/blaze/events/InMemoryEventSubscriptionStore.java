package com.nollen.blaze.events;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
			BlazeEventType.from(rs.getString("type")),
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
			Timestamp createdAt = Timestamp.from(snapshot.createdAt());
			int updated = jdbc.update(
				"UPDATE event_subscriptions SET type = ?, version = ?, channel_id = ?, session_id = ?, created_at = ? WHERE id = ?",
				snapshot.type().name(), snapshot.version(), snapshot.channelId(), snapshot.sessionId(), createdAt, snapshot.id());
			if (updated == 0) {
				jdbc.update(
					"INSERT INTO event_subscriptions (type, version, channel_id, session_id, created_at, id) VALUES (?, ?, ?, ?, ?, ?)",
				snapshot.type().name(), snapshot.version(), snapshot.channelId(), snapshot.sessionId(), createdAt, snapshot.id());
			}

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

	public Optional<EventSubscriptionSnapshot> findByChannelIdAndType(String channelId, BlazeEventType type) {
		if (jdbc != null) {
			return jdbc.query(
				"SELECT * FROM event_subscriptions WHERE channel_id = ? AND type = ? ORDER BY created_at DESC LIMIT 1",
				mapper, channelId, type.name()).stream().findFirst();
		}
		return subscriptions.values().stream()
			.filter(s -> channelId.equals(s.channelId()) && s.type() == type)
			.findFirst();
	}

	public void delete(String id) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM event_subscriptions WHERE id = ?", id);
			return;
		}
		subscriptions.remove(id);
	}

	public void clear() {
		if (jdbc != null) {
			jdbc.update("DELETE FROM event_subscriptions");
			return;
		}
		subscriptions.clear();
	}
}
