package com.blaze.eventhub.event;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventActionRuleStore implements EventActionRuleStore {

    private final JdbcTemplate jdbc;

    private final RowMapper<EventActionRule> rowMapper = (ResultSet rs, int rowNum) ->
            new EventActionRule(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    ActionType.fromString(rs.getString("action_type")),
                    rs.getBoolean("enabled"),
                    rs.getInt("weight"),
                    TierMode.fromString(rs.getString("mode")),
                    rs.getTimestamp("created_at").toInstant()
            );

    public JdbcEventActionRuleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EventActionRule> findByEventId(String eventId) {
        return jdbc.query(
                "SELECT * FROM event_action_rules WHERE event_id = ? ORDER BY action_type",
                rowMapper, eventId);
    }

    @Override
    public Optional<EventActionRule> findByEventIdAndType(String eventId, ActionType actionType) {
        List<EventActionRule> results = jdbc.query(
                "SELECT * FROM event_action_rules WHERE event_id = ? AND action_type = ?",
                rowMapper, eventId, actionType.value());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void save(EventActionRule rule) {
        jdbc.update("""
                INSERT INTO event_action_rules (id, event_id, action_type, enabled, weight, mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, action_type)
                DO UPDATE SET enabled = EXCLUDED.enabled, weight = EXCLUDED.weight, mode = EXCLUDED.mode
                """,
                rule.id(), rule.eventId(), rule.actionType().value(),
                rule.enabled(), rule.weight(), rule.mode().value(),
                Timestamp.from(rule.createdAt()));
    }

    @Override
    public void saveAll(List<EventActionRule> rules) {
        if (rules.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO event_action_rules (id, event_id, action_type, enabled, weight, mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, action_type)
                DO UPDATE SET enabled = EXCLUDED.enabled, weight = EXCLUDED.weight, mode = EXCLUDED.mode
                """;
        jdbc.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EventActionRule rule = rules.get(i);
                ps.setString(1, rule.id());
                ps.setString(2, rule.eventId());
                ps.setString(3, rule.actionType().value());
                ps.setBoolean(4, rule.enabled());
                ps.setInt(5, rule.weight());
                ps.setString(6, rule.mode().value());
                ps.setTimestamp(7, Timestamp.from(rule.createdAt()));
            }

            @Override
            public int getBatchSize() {
                return rules.size();
            }
        });
    }

    @Override
    public void deleteByEventId(String eventId) {
        jdbc.update("DELETE FROM event_action_rules WHERE event_id = ?", eventId);
    }
}
