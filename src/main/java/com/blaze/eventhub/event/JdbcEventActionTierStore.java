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
public class JdbcEventActionTierStore implements EventActionTierStore {

    private final JdbcTemplate jdbc;

    private final RowMapper<EventActionTier> rowMapper = (ResultSet rs, int rowNum) ->
            new EventActionTier(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    ActionType.fromString(rs.getString("action_type")),
                    rs.getInt("threshold"),
                    rs.getInt("entries"),
                    rs.getInt("tier_order"),
                    rs.getTimestamp("created_at").toInstant()
            );

    public JdbcEventActionTierStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EventActionTier> findByEventId(String eventId) {
        return jdbc.query(
                "SELECT * FROM event_action_tiers WHERE event_id = ? ORDER BY action_type, tier_order",
                rowMapper, eventId);
    }

    @Override
    public List<EventActionTier> findByEventIdAndType(String eventId, ActionType actionType) {
        return jdbc.query(
                "SELECT * FROM event_action_tiers WHERE event_id = ? AND action_type = ? ORDER BY tier_order",
                rowMapper, eventId, actionType.value());
    }

    @Override
    public Optional<EventActionTier> findByEventIdAndTypeAndThreshold(String eventId, ActionType actionType, int threshold) {
        List<EventActionTier> results = jdbc.query(
                "SELECT * FROM event_action_tiers WHERE event_id = ? AND action_type = ? AND threshold = ?",
                rowMapper, eventId, actionType.value(), threshold);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void save(EventActionTier tier) {
        jdbc.update("""
                INSERT INTO event_action_tiers (id, event_id, action_type, threshold, entries, tier_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, action_type, threshold)
                DO UPDATE SET entries = EXCLUDED.entries, tier_order = EXCLUDED.tier_order
                """,
                tier.id(), tier.eventId(), tier.actionType().value(),
                tier.threshold(), tier.entries(), tier.tierOrder(),
                Timestamp.from(tier.createdAt()));
    }

    @Override
    public void saveAll(List<EventActionTier> tiers) {
        if (tiers.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO event_action_tiers (id, event_id, action_type, threshold, entries, tier_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, action_type, threshold)
                DO UPDATE SET entries = EXCLUDED.entries, tier_order = EXCLUDED.tier_order
                """;
        jdbc.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EventActionTier tier = tiers.get(i);
                ps.setString(1, tier.id());
                ps.setString(2, tier.eventId());
                ps.setString(3, tier.actionType().value());
                ps.setInt(4, tier.threshold());
                ps.setInt(5, tier.entries());
                ps.setInt(6, tier.tierOrder());
                ps.setTimestamp(7, Timestamp.from(tier.createdAt()));
            }

            @Override
            public int getBatchSize() {
                return tiers.size();
            }
        });
    }

    @Override
    public void deleteByEventId(String eventId) {
        jdbc.update("DELETE FROM event_action_tiers WHERE event_id = ?", eventId);
    }

    @Override
    public void deleteByEventIdAndType(String eventId, ActionType actionType) {
        jdbc.update("DELETE FROM event_action_tiers WHERE event_id = ? AND action_type = ?", eventId, actionType.value());
    }
}