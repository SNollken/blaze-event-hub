package com.blaze.eventhub.event;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventActionTierStore implements EventActionTierStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_action_tiers (id, event_id, action_type, threshold, entries, tier_order, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_SQL = """
            UPDATE event_action_tiers SET entries = ?, tier_order = ?
            WHERE event_id = ? AND action_type = ? AND threshold = ?
            """;

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
        try {
            jdbc.update(INSERT_SQL,
                    tier.id(), tier.eventId(), tier.actionType().value(),
                    tier.threshold(), tier.entries(), tier.tierOrder(),
                    Timestamp.from(tier.createdAt()));
        } catch (DuplicateKeyException e) {
            jdbc.update(UPDATE_SQL,
                    tier.entries(), tier.tierOrder(),
                    tier.eventId(), tier.actionType().value(), tier.threshold());
        }
    }

    @Override
    public void saveAll(List<EventActionTier> tiers) {
        if (tiers.isEmpty()) {
            return;
        }
        // ponytail: batch upsert for production; try-catch per row for H2 test compat
        for (EventActionTier tier : tiers) {
            save(tier);
        }
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
