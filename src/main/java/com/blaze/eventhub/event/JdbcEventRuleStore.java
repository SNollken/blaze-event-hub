package com.blaze.eventhub.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventRuleStore implements EventRuleStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<EventRule> ROW_MAPPER = new EventRuleRowMapper();

    public JdbcEventRuleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EventRule> findByEventId(String eventId) {
        return jdbc.query("SELECT * FROM event_rules WHERE event_id = ? ORDER BY created_at", ROW_MAPPER, eventId);
    }

    @Override
    public EventRule save(EventRule rule) {
        jdbc.update("""
                INSERT INTO event_rules (id, event_id, action_type, threshold_amount, entries,
                    is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rule.id(),
                rule.eventId(),
                rule.actionType().name().toLowerCase(),
                rule.thresholdAmount(),
                rule.entries(),
                rule.isActive(),
                Timestamp.from(rule.createdAt()),
                Timestamp.from(rule.updatedAt()));
        return rule;
    }

    @Override
    public int update(EventRule rule) {
        return jdbc.update("""
                UPDATE event_rules
                SET action_type = ?, threshold_amount = ?, entries = ?, is_active = ?, updated_at = ?
                WHERE id = ?
                """,
                rule.actionType().name().toLowerCase(),
                rule.thresholdAmount(),
                rule.entries(),
                rule.isActive(),
                Timestamp.from(rule.updatedAt()),
                rule.id());
    }

    @Override
    public int delete(String id) {
        return jdbc.update("DELETE FROM event_rules WHERE id = ?", id);
    }

    private static class EventRuleRowMapper implements RowMapper<EventRule> {
        @Override
        public EventRule mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EventRule(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    ActionType.fromDb(rs.getString("action_type")),
                    rs.getInt("threshold_amount"),
                    rs.getInt("entries"),
                    rs.getBoolean("is_active"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
