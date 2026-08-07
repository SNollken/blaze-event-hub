package com.nollen.blaze.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.Timestamp;
import org.springframework.stereotype.Repository;

@Repository
public class BlazeEventsLogStore {

	private static final int MAX_ENTRIES = 500;

	private final ConcurrentLinkedDeque<BlazeEventsLogEntry> entries = new ConcurrentLinkedDeque<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<BlazeEventsLogEntry> mapper = (rs, rowNum) -> {
		Timestamp ts = rs.getTimestamp("received_at");
		java.time.Instant instant = ts != null ? ts.toInstant() : java.time.Instant.now();
		return new BlazeEventsLogEntry(
			rs.getString("id"),
			instant,
			rs.getString("event_type"),
			rs.getString("source"),
			rs.getString("message"),
			rs.getString("raw_payload"));
	};

	public BlazeEventsLogStore() {
		this.jdbc = null;
	}

	@Autowired
	public BlazeEventsLogStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void append(BlazeEventsLogEntry entry) {
		if (jdbc != null) {
			Timestamp receivedAt = Timestamp.from(entry.timestamp());
			int updated = jdbc.update(
				"UPDATE blaze_events_log SET received_at = ?, event_type = ?, source = ?, message = ?, raw_payload = ? WHERE id = ?",
				receivedAt, entry.eventType(), entry.source(), entry.message(), entry.data(), entry.id());
			if (updated == 0) {
				jdbc.update(
					"INSERT INTO blaze_events_log (received_at, event_type, source, message, raw_payload, id) VALUES (?, ?, ?, ?, ?, ?)",
					receivedAt, entry.eventType(), entry.source(), entry.message(), entry.data(), entry.id());
			}

			return;
		}
		entries.addLast(entry);
		while (entries.size() > MAX_ENTRIES) {
			entries.pollFirst();
		}
	}

	public List<BlazeEventsLogEntry> list(String eventType, String source, int limit) {
		if (jdbc != null) {
			int resolvedLimit = limit > 0 ? limit : 50;
			if (eventType != null && !eventType.isBlank() && source != null && !source.isBlank()) {
				return jdbc.query("SELECT * FROM blaze_events_log WHERE LOWER(event_type) = LOWER(?) AND LOWER(source) = LOWER(?) ORDER BY received_at DESC LIMIT ?",
						mapper, eventType, source, resolvedLimit);
			}
			if (eventType != null && !eventType.isBlank()) {
				return jdbc.query("SELECT * FROM blaze_events_log WHERE LOWER(event_type) = LOWER(?) ORDER BY received_at DESC LIMIT ?",
						mapper, eventType, resolvedLimit);
			}
			if (source != null && !source.isBlank()) {
				return jdbc.query("SELECT * FROM blaze_events_log WHERE LOWER(source) = LOWER(?) ORDER BY received_at DESC LIMIT ?",
						mapper, source, resolvedLimit);
			}
			return jdbc.query("SELECT * FROM blaze_events_log ORDER BY received_at DESC LIMIT ?", mapper, resolvedLimit);
		}
		return entries.stream()
				.filter(e -> eventType == null || eventType.isBlank() || eventType.equalsIgnoreCase(e.eventType()))
				.filter(e -> source == null || source.isBlank() || source.equalsIgnoreCase(e.source()))
				.sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
				.limit(limit > 0 ? limit : 50)
				.toList();
	}

	public List<BlazeEventsLogEntry> listAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM blaze_events_log ORDER BY received_at", mapper);
		}
		return new ArrayList<>(entries);
	}

	public void clear() {
		if (jdbc != null) {
			jdbc.update("DELETE FROM blaze_events_log");
			return;
		}
		entries.clear();
	}

	public long count() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM blaze_events_log", Long.class);
			return count == null ? 0 : count;
		}
		return entries.size();
	}

	public Instant findLastTimestamp() {
		if (jdbc != null) {
			Timestamp ts = jdbc.queryForObject("SELECT MAX(received_at) FROM blaze_events_log", Timestamp.class);
			return ts != null ? ts.toInstant() : null;
		}
		return entries.stream()
				.map(BlazeEventsLogEntry::timestamp)
				.max(Instant::compareTo)
				.orElse(null);
	}
}
