package com.blaze.eventhub.event.entry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventEntryStore implements EventEntryStore {

    private final JdbcTemplate jdbc;

    public JdbcEventEntryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EventEntry save(EventEntry entry) {
        jdbc.update("""
                INSERT INTO event_entries (id, event_id, member_id, detected_action_id,
                    action_type, amount, entries_granted, calculation_reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.id(),
                entry.eventId(),
                entry.memberId(),
                entry.detectedActionId(),
                entry.actionType(),
                entry.amount(),
                entry.entriesGranted(),
                entry.calculationReason(),
                toTimestamp(entry.createdAt()));
        return entry;
    }

    @Override
    public List<EventEntry> findByEventId(String eventId) {
        return jdbc.query("SELECT * FROM event_entries WHERE event_id = ? ORDER BY created_at DESC",
                new EventEntryRowMapper(), eventId);
    }

    @Override
    public List<EventEntry> findByMemberId(String memberId) {
        return jdbc.query("SELECT * FROM event_entries WHERE member_id = ? ORDER BY created_at DESC",
                new EventEntryRowMapper(), memberId);
    }

    @Override
    public List<EventEntry> findByEventIdAndMemberId(String eventId, String memberId) {
        return jdbc.query("SELECT * FROM event_entries WHERE event_id = ? AND member_id = ? ORDER BY created_at DESC",
                new EventEntryRowMapper(), eventId, memberId);
    }

    @Override
    public int countByEventId(String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_entries WHERE event_id = ?",
                Integer.class, eventId);
        return count != null ? count : 0;
    }

    @Override
    public int countByEventIdAndMemberId(String eventId, String memberId) {
        Integer sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(entries_granted), 0) FROM event_entries WHERE event_id = ? AND member_id = ?",
                Integer.class, eventId, memberId);
        return sum != null ? sum : 0;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static class EventEntryRowMapper implements RowMapper<EventEntry> {
        @Override
        public EventEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EventEntry(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    rs.getString("member_id"),
                    rs.getString("detected_action_id"),
                    rs.getString("action_type"),
                    rs.getInt("amount"),
                    rs.getInt("entries_granted"),
                    rs.getString("calculation_reason"),
                    toInstant(rs.getTimestamp("created_at")));
        }
    }
}
