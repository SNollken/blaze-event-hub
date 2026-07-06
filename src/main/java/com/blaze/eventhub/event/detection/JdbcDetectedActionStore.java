package com.blaze.eventhub.event.detection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDetectedActionStore implements DetectedActionStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<DetectedAction> ROW_MAPPER = new DetectedActionRowMapper();

    public JdbcDetectedActionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DetectedAction save(DetectedAction action) {
        jdbc.update("""
                MERGE INTO detected_actions (id, idempotency_hash, event_id, member_id, action_type,
                    target_channel_id, actor_blaze_user_id, actor_username, actor_wallet_address,
                    amount, raw_payload, detected_at, processed, created_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                action.id(),
                action.idempotencyHash(),
                action.eventId(),
                action.memberId(),
                action.actionType(),
                action.targetChannelId(),
                action.actorBlazeUserId(),
                action.actorUsername(),
                action.actorWalletAddress(),
                action.amount(),
                action.rawPayload(),
                toTimestamp(action.detectedAt()),
                action.processed(),
                toTimestamp(action.createdAt()));
        return action;
    }

    @Override
    public Optional<DetectedAction> findByIdempotencyHash(String hash) {
        List<DetectedAction> result = jdbc.query(
                "SELECT * FROM detected_actions WHERE idempotency_hash = ?",
                ROW_MAPPER, hash);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<DetectedAction> findByEventId(String eventId) {
        return jdbc.query(
                "SELECT * FROM detected_actions WHERE event_id = ? ORDER BY detected_at ASC",
                ROW_MAPPER, eventId);
    }

    @Override
    public List<DetectedAction> findByMemberId(String memberId) {
        return jdbc.query(
                "SELECT * FROM detected_actions WHERE member_id = ? ORDER BY detected_at DESC",
                ROW_MAPPER, memberId);
    }

    @Override
    public List<DetectedAction> findByEventIdAndMemberId(String eventId, String memberId) {
        return jdbc.query(
                "SELECT * FROM detected_actions WHERE event_id = ? AND member_id = ? ORDER BY detected_at ASC",
                ROW_MAPPER, eventId, memberId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class DetectedActionRowMapper implements RowMapper<DetectedAction> {
        @Override
        public DetectedAction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DetectedAction(
                    rs.getString("id"),
                    rs.getString("idempotency_hash"),
                    rs.getString("event_id"),
                    rs.getString("member_id"),
                    rs.getString("action_type"),
                    rs.getString("target_channel_id"),
                    rs.getString("actor_blaze_user_id"),
                    rs.getString("actor_username"),
                    rs.getString("actor_wallet_address"),
                    rs.getInt("amount"),
                    rs.getString("raw_payload"),
                    toInstant(rs.getTimestamp("detected_at")),
                    rs.getBoolean("processed"),
                    toInstant(rs.getTimestamp("created_at")));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
