package com.nollen.blaze.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BlazeEventsLogStore {

	private static final int MAX_ENTRIES = 500;

	private final ConcurrentLinkedDeque<BlazeEventsLogEntry> entries = new ConcurrentLinkedDeque<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<BlazeEventsLogEntry> mapper = (rs, rowNum) -> new BlazeEventsLogEntry(
			rs.getString("id"),
			rs.getTimestamp("received_at").toInstant(),
			rs.getString("event_type"),
			rs.getString("source"),
			rs.getString("message"),
			rs.getString("raw_payload"));

	public BlazeEventsLogStore() {
		this.jdbc = null;
	}

	@Autowired
	public BlazeEventsLogStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void append(BlazeEventsLogEntry entry) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO blaze_events_log KEY(id)
					VALUES (?, ?, ?, ?, ?, ?)
					""",
					entry.id(),
					entry.timestamp(),
					entry.eventType(),
					entry.source(),
					entry.message(),
					entry.data());
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
				.collect(Collectors.toList());
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
}
