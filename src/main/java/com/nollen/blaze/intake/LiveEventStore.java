package com.nollen.blaze.intake;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.nollen.blaze.common.JsonData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LiveEventStore {

	private final ConcurrentHashMap<String, LiveEvent> events = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<LiveEvent> mapper = (rs, rowNum) -> new LiveEvent(
			rs.getString("id"),
			LiveEventType.valueOf(rs.getString("type")),
			LiveEventSource.valueOf(rs.getString("source")),
			LiveEventStatus.valueOf(rs.getString("status")),
			JsonData.readMap(rs.getString("payload")),
			rs.getTimestamp("occurred_at").toInstant(),
			rs.getString("dedup_key"));

	public LiveEventStore() {
		this.jdbc = null;
	}

	@Autowired
	public LiveEventStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public LiveEvent save(LiveEvent event) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO live_events KEY(id)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""",
					event.id(),
					event.type().name(),
					event.source().name(),
					event.status().name(),
					JsonData.write(event.payload()),
					event.timestamp(),
					event.dedupKey());
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
			return jdbc.query("SELECT * FROM live_events ORDER BY occurred_at DESC", mapper);
		}
		return events.values().stream()
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findByType(LiveEventType type) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE type = ? ORDER BY occurred_at DESC", mapper, type.name());
		}
		return events.values().stream()
				.filter(e -> e.type() == type)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findBySource(LiveEventSource source) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE source = ? ORDER BY occurred_at DESC", mapper, source.name());
		}
		return events.values().stream()
				.filter(e -> e.source() == source)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findByStatus(LiveEventStatus status) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE status = ? ORDER BY occurred_at DESC", mapper, status.name());
		}
		return events.values().stream()
				.filter(e -> e.status() == status)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
	}

	public List<LiveEvent> findByTypeAndSource(LiveEventType type, LiveEventSource source) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM live_events WHERE type = ? AND source = ? ORDER BY occurred_at DESC",
					mapper, type.name(), source.name());
		}
		return events.values().stream()
				.filter(e -> e.type() == type && e.source() == source)
				.sorted(Comparator.comparing(LiveEvent::timestamp).reversed())
				.toList();
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

	public void clear() {
		if (jdbc != null) {
			jdbc.update("DELETE FROM live_events");
			return;
		}
		events.clear();
	}
}
