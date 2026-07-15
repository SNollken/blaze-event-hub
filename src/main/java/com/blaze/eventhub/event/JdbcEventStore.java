package com.blaze.eventhub.event;

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
public class JdbcEventStore implements EventStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Event> ROW_MAPPER = new EventRowMapper();

    public JdbcEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Event insert(Event event) {
        jdbc.update("""
                INSERT INTO events (id, creator_member_id, creator_blaze_user_id, creator_channel_id,
                    creator_channel_slug, creator_channel_display_name, creator_channel_avatar_url,
                    title, description, x_post_url, prize, entry_command, status,
                    finalized_participant_count, finalized_pool_hash,
                    starts_at, ends_at, created_at, updated_at, opened_at,
                    finalization_cutoff_at, finalization_attempt_id, closed_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.id(),
                event.creatorMemberId(),
                event.creatorBlazeUserId(),
                event.creatorChannelId(),
                event.creatorChannelSlug(),
                event.creatorChannelDisplayName(),
                event.creatorChannelAvatarUrl(),
                event.title(),
                event.description(),
                event.xPostUrl(),
                event.prize(),
                event.entryCommand(),
                event.status().name().toLowerCase(),
                event.finalizedParticipantCount(),
                event.finalizedPoolHash(),
                toTimestamp(event.startsAt()),
                toTimestamp(event.endsAt()),
                toTimestamp(event.createdAt()),
                toTimestamp(event.updatedAt()),
                toTimestamp(event.openedAt()),
                toTimestamp(event.finalizationCutoffAt()),
                event.finalizationAttemptId(),
                toTimestamp(event.closedAt()),
                toTimestamp(event.completedAt()));
        return event;
    }

    @Override
    public int updateDraft(Event event) {
        return jdbc.update("""
                UPDATE events
                SET title = ?, description = ?, x_post_url = ?, prize = ?, entry_command = ?,
                    starts_at = ?, ends_at = ?, updated_at = ?
                WHERE id = ? AND status = 'draft'
                """,
                event.title(),
                event.description(),
                event.xPostUrl(),
                event.prize(),
                event.entryCommand(),
                toTimestamp(event.startsAt()),
                toTimestamp(event.endsAt()),
                toTimestamp(event.updatedAt()),
                event.id());
    }

    @Override
    public Optional<Event> findById(String id) {
        List<Event> result = jdbc.query("SELECT * FROM events WHERE id = ?", ROW_MAPPER, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Optional<Event> findByIdForUpdate(String id) {
        List<Event> result = jdbc.query("SELECT * FROM events WHERE id = ? FOR UPDATE", ROW_MAPPER, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Event> findByCreatorMemberId(String memberId) {
        return jdbc.query("SELECT * FROM events WHERE creator_member_id = ? ORDER BY created_at DESC", ROW_MAPPER, memberId);
    }

    @Override
    public List<Event> findByStatus(EventStatus status) {
        return jdbc.query("SELECT * FROM events WHERE status = ? ORDER BY created_at DESC", ROW_MAPPER, status.name().toLowerCase());
    }

    @Override
    public List<Event> findCapturingByChannelId(String channelId) {
        return jdbc.query("""
                SELECT * FROM events
                WHERE creator_channel_id = ? AND status IN ('open', 'finalizing')
                ORDER BY created_at
                """, ROW_MAPPER, channelId);
    }

    @Override
    public List<Event> findAll() {
        return jdbc.query("SELECT * FROM events ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public int cancelEvent(String id, Instant cancelledAt) {
        return jdbc.update("""
                UPDATE events
                SET status = 'cancelled', active_capture_key = NULL, updated_at = ?
                WHERE id = ? AND status IN ('draft', 'open')
                """, Timestamp.from(cancelledAt), id);
    }

    @Override
    public int openEvent(String id, String activeCaptureKey, Instant openedAt) {
        return jdbc.update("""
                UPDATE events
                SET status = 'open', active_capture_key = ?, opened_at = ?, updated_at = ?
                WHERE id = ? AND status = 'draft'
                """, activeCaptureKey, Timestamp.from(openedAt), Timestamp.from(openedAt), id);
    }

    @Override
    public int beginFinalization(String id, Instant cutoffAt, String attemptId) {
        return jdbc.update("""
                UPDATE events
                SET status = 'finalizing', finalization_cutoff_at = ?,
                    finalization_attempt_id = ?, updated_at = ?
                WHERE id = ? AND status = 'open'
                """, Timestamp.from(cutoffAt), attemptId, Timestamp.from(cutoffAt), id);
    }

    @Override
    public int abortFinalization(String id, String attemptId, Instant updatedAt) {
        return jdbc.update("""
                UPDATE events
                SET status = 'open', finalization_cutoff_at = NULL,
                    finalization_attempt_id = NULL, updated_at = ?
                WHERE id = ? AND status = 'finalizing' AND finalization_attempt_id = ?
                """, Timestamp.from(updatedAt), id, attemptId);
    }

    @Override
    public int finalizeEvent(
            String id,
            String attemptId,
            Instant closedAt,
            int participantCount,
            String poolHash) {
        return jdbc.update("""
                UPDATE events
                SET status = 'closed', closed_at = ?, finalized_participant_count = ?,
                    finalized_pool_hash = ?, active_capture_key = NULL,
                    finalization_attempt_id = NULL, updated_at = ?
                WHERE id = ? AND status = 'finalizing' AND finalization_attempt_id = ?
                """,
                Timestamp.from(closedAt),
                participantCount,
                poolHash,
                Timestamp.from(closedAt),
                id,
                attemptId);
    }

    @Override
    public int recoverStaleFinalizations(Instant staleBefore, Instant updatedAt) {
        return jdbc.update("""
                UPDATE events
                SET status = 'open', finalization_cutoff_at = NULL,
                    finalization_attempt_id = NULL, updated_at = ?
                WHERE status = 'finalizing' AND updated_at < ?
                """, Timestamp.from(updatedAt), Timestamp.from(staleBefore));
    }

    @Override
    public int completeEvent(String id, Instant completedAt) {
        return jdbc.update("""
                UPDATE events
                SET status = 'completed', completed_at = ?, active_capture_key = NULL, updated_at = ?
                WHERE id = ? AND status = 'closed'
                """, Timestamp.from(completedAt), Timestamp.from(completedAt), id);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class EventRowMapper implements RowMapper<Event> {
        @Override
        public Event mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Event(
                    rs.getString("id"),
                    rs.getString("creator_member_id"),
                    rs.getString("creator_blaze_user_id"),
                    rs.getString("creator_channel_id"),
                    rs.getString("creator_channel_slug"),
                    rs.getString("creator_channel_display_name"),
                    rs.getString("creator_channel_avatar_url"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("x_post_url"),
                    rs.getString("prize"),
                    rs.getString("entry_command"),
                    EventStatus.fromDb(rs.getString("status")),
                    rs.getInt("finalized_participant_count"),
                    rs.getString("finalized_pool_hash"),
                    toInstant(rs.getTimestamp("starts_at")),
                    toInstant(rs.getTimestamp("ends_at")),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")),
                    toInstant(rs.getTimestamp("opened_at")),
                    toInstant(rs.getTimestamp("finalization_cutoff_at")),
                    rs.getString("finalization_attempt_id"),
                    toInstant(rs.getTimestamp("closed_at")),
                    toInstant(rs.getTimestamp("completed_at")));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
