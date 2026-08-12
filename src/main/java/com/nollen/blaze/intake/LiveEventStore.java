package com.nollen.blaze.intake;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.nollen.blaze.common.JsonData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LiveEventStore {

	// Listing queries are bounded: live_events has no retention, so an unbounded
	// SELECT * eventually OOMs the JVM / blows up the JSON payload. 500 most
	// recent rows is plenty for the streamer dashboard (stats use COUNT queries).
	public static final int LIST_LIMIT = 500;

	// Time-based retention: stream-event history is only useful for a limited
	// window. Listings are already bounded (LIST_LIMIT) but the rows themselves
	// lived forever — the table and every COUNT query grew without bound.
	// RETENTION_MIN_ROWS avoids running the DELETE on small tables; the
	// occurred_at index (r50) keeps the DELETE cheap when it does run.
	public static final int RETENTION_DAYS = 30;
	public static final int RETENTION_MIN_ROWS = 1000;

	private final ConcurrentHashMap<String, LiveEvent> events = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<LiveEvent> mapper = (rs, rowNum) -> {
		Timestamp ts = rs.getTimestamp("occurred_at");
		// Null-safe fallback: a NULL occurred_at means corrupted data; EPOCH sorts the
		// row as oldest (consistent with the other stores) so retention deletes it
		// first instead of it masquerading as the newest event.
		Instant instant = ts != null ? ts.toInstant() : Instant.EPOCH;
		return new LiveEvent(
			rs.getString("id"),
			LiveEventType.valueOf(rs.getString("type")),
			LiveEventSource.valueOf(rs.getString("source")),
			LiveEventStatus.valueOf(rs.getString("status")),
			JsonData.readMap(rs.getString("payload")),
			instant,
			rs.getString("dedup_key"));
	};

	public LiveEventStore() {
		this.jdbc = null;
	}

	@Autowired
	public LiveEventStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public LiveEvent save(LiveEvent event) {
		if (jdbc != null) {
			Timestamp occurredAt = Timestamp.from(event.timestamp());
			int updated = jdbc.update(
				"UPDATE live_events SET type = ?, source = ?, status = ?, payload = ?, occurred_at = ?, dedup_key = ? WHERE id = ?",
				event.type().name(), event.source().name(), event.status().name(), JsonData.write(event.payload()), occurredAt, event.dedupKey(), event.id());
			if (updated == 0) {
				jdbc.update(
					"INSERT INTO live_events (type, source, status, payload, occurred_at, dedup_key, id) VALUES (?, ?, ?, ?, ?, ?, ?)",
					event.type().name(), event.source().name(), event.status().name(), JsonData.write(event.payload()), occurredAt, event.dedupKey(), event.id());
				applyRetention(Instant.now());
			}

			return event;
		}
		events.put(event.id(), event);
		return event;
	}

	public Optional<LiveEvent> findById(String id) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE id = ?", mapper, id).stream().findFirst();
		}
		return Optional.ofNullable(events.get(id));
	}

	public List<LiveEvent> listAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events ORDER BY occurred_at DESC LIMIT ?", mapper, LIST_LIMIT);
		}
		return events.values().stream()
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.limit(LIST_LIMIT)
				.toList();
	}

	public List<LiveEvent> findByType(LiveEventType type) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE type = ? ORDER BY occurred_at DESC LIMIT ?", mapper, type.name(), LIST_LIMIT);
		}
		return events.values().stream()
				.filter(e -> e.type() == type)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.limit(LIST_LIMIT)
				.toList();
	}

	public List<LiveEvent> findBySource(LiveEventSource source) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE source = ? ORDER BY occurred_at DESC LIMIT ?", mapper, source.name(), LIST_LIMIT);
		}
		return events.values().stream()
				.filter(e -> e.source() == source)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.limit(LIST_LIMIT)
				.toList();
	}

	public List<LiveEvent> findByStatus(LiveEventStatus status) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE status = ? ORDER BY occurred_at DESC LIMIT ?", mapper, status.name(), LIST_LIMIT);
		}
		return events.values().stream()
				.filter(e -> e.status() == status)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.limit(LIST_LIMIT)
				.toList();
	}

	public List<LiveEvent> findByTypeAndSource(LiveEventType type, LiveEventSource source) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE type = ? AND source = ? ORDER BY occurred_at DESC LIMIT ?",
					mapper, type.name(), source.name(), LIST_LIMIT);
		}
		return events.values().stream()
				.filter(e -> e.type() == type && e.source() == source)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.limit(LIST_LIMIT)
				.toList();
	}

	public boolean existsByDedupKey(String dedupKey) {
		if (jdbc != null) {
			Integer count = jdbc.queryForObject(
					"SELECT COUNT(*) FROM live_events WHERE dedup_key = ? AND status != 'REJECTED'",
					Integer.class, dedupKey);
			return count != null && count > 0;
		}
		return events.values().stream()
				.anyMatch(e -> dedupKey.equals(e.dedupKey()) && e.status() != LiveEventStatus.REJECTED);
	}

	public long count() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM live_events", Long.class);
			return count == null ? 0 : count;
		}
		return events.size();
	}

	public long countByStatus(LiveEventStatus status) {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM live_events WHERE status = ?", Long.class, status.name());
			return count == null ? 0 : count;
		}
		return events.values().stream().filter(e -> e.status() == status).count();
	}

	public Map<LiveEventStatus, Long> countByStatusGrouped() {
		if (jdbc != null) {
			return jdbc.query(
					"SELECT status, COUNT(*) FROM live_events GROUP BY status",
					(rs, rowNum) -> Map.entry(
							LiveEventStatus.valueOf(rs.getString("status")),
							rs.getLong(2)))
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}
		return events.values().stream()
				.collect(Collectors.groupingBy(
						LiveEvent::status,
						Collectors.counting()));
	}

	public void clear() {
		if (jdbc != null) {
			jdbc.update("DELETE FROM live_events");
			return;
		}
		events.clear();
	}

	/**
	 * Deletes rows older than {@link #RETENTION_DAYS} days. Runs only after a
	 * fresh INSERT (not on upsert-update) and only when the table actually
	 * holds more than {@link #RETENTION_MIN_ROWS} rows — same shape as
	 * {@code BlazeEventsLogStore.applyRetention} (r50).
	 */
	void applyRetention(Instant now) {
		Long total = jdbc.queryForObject("SELECT COUNT(*) FROM live_events", Long.class);
		if (total == null || total <= RETENTION_MIN_ROWS) {
			return;
		}
		Timestamp cutoff = Timestamp.from(now.minus(RETENTION_DAYS, ChronoUnit.DAYS));
		jdbc.update("DELETE FROM live_events WHERE occurred_at < ?", cutoff);
	}
}
