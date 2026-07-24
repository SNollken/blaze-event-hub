package com.blaze.eventhub.event;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventActionRuleStore implements EventActionRuleStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_action_rules (id, event_id, action_type, enabled, weight, mode, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_SQL = """
            UPDATE event_action_rules SET enabled = ?, weight = ?, mode = ?
            WHERE event_id = ? AND action_type = ?
            """;

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
        try {
            jdbc.update(INSERT_SQL,
                    rule.id(), rule.eventId(), rule.actionType().value(),
                    rule.enabled(), rule.weight(), rule.mode().value(),
                    Timestamp.from(rule.createdAt()));
        } catch (DuplicateKeyException e) {
            jdbc.update(UPDATE_SQL,
                    rule.enabled(), rule.weight(), rule.mode().value(),
                    rule.eventId(), rule.actionType().value());
        }
    }

    @Override
    public void saveAll(List<EventActionRule> rules) {
        if (rules.isEmpty()) {
            return;
        }
        // ponytail: batch upsert for production PostgreSQL; try-catch per row for H2 test compat
        for (EventActionRule rule : rules) {
            save(rule);
        }
    }

    @Override
    public void deleteByEventId(String eventId) {
        jdbc.update("DELETE FROM event_action_rules WHERE event_id = ?", eventId);
    }
}
