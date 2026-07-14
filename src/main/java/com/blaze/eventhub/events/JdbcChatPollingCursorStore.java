package com.blaze.eventhub.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChatPollingCursorStore implements ChatPollingCursorStore {

    private static final RowMapper<ChatPollingCursor> ROW_MAPPER = new CursorRowMapper();

    private final JdbcTemplate jdbc;

    public JdbcChatPollingCursorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChatPollingCursor> find(String memberId, String channelId) {
        List<ChatPollingCursor> results = jdbc.query("""
                SELECT * FROM chat_polling_cursors WHERE member_id = ? AND channel_id = ?
                """, ROW_MAPPER, memberId, channelId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void markSuccess(
            String memberId,
            String channelId,
            String eventId,
            String lastMessageId,
            Instant polledAt) {
        int updated = jdbc.update("""
                UPDATE chat_polling_cursors
                SET event_id = ?, last_message_id = ?, scan_cursor = NULL,
                    scan_anchor_message_id = NULL, last_polled_at = ?,
                    last_success_at = ?, last_error_code = NULL
                WHERE member_id = ? AND channel_id = ?
                  AND (last_polled_at IS NULL OR last_polled_at <= ?)
                """, eventId, lastMessageId, timestamp(polledAt), timestamp(polledAt),
                memberId, channelId, timestamp(polledAt));
        if (updated == 0 && find(memberId, channelId).isEmpty()) {
            try {
                jdbc.update("""
                        INSERT INTO chat_polling_cursors (
                            member_id, channel_id, event_id, last_message_id,
                            scan_cursor, scan_anchor_message_id, last_polled_at,
                            last_success_at, last_error_code)
                        VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, NULL)
                        """, memberId, channelId, eventId, lastMessageId,
                        timestamp(polledAt), timestamp(polledAt));
            } catch (DuplicateKeyException concurrentInsert) {
                markSuccess(memberId, channelId, eventId, lastMessageId, polledAt);
            }
        }
    }

    @Override
    public void markBackfillProgress(
            String memberId,
            String channelId,
            String eventId,
            String lastMessageId,
            String scanCursor,
            String scanAnchorMessageId,
            Instant polledAt) {
        int updated = jdbc.update("""
                UPDATE chat_polling_cursors
                SET event_id = ?, last_message_id = ?, scan_cursor = ?,
                    scan_anchor_message_id = ?, last_polled_at = ?,
                    last_error_code = 'CHAT_BACKFILL_PENDING'
                WHERE member_id = ? AND channel_id = ?
                  AND (last_polled_at IS NULL OR last_polled_at <= ?)
                """, eventId, lastMessageId, scanCursor, scanAnchorMessageId,
                timestamp(polledAt), memberId, channelId, timestamp(polledAt));
        if (updated == 0 && find(memberId, channelId).isEmpty()) {
            try {
                jdbc.update("""
                        INSERT INTO chat_polling_cursors (
                            member_id, channel_id, event_id, last_message_id,
                            scan_cursor, scan_anchor_message_id, last_polled_at,
                            last_success_at, last_error_code)
                        VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'CHAT_BACKFILL_PENDING')
                        """, memberId, channelId, eventId, lastMessageId, scanCursor,
                        scanAnchorMessageId, timestamp(polledAt));
            } catch (DuplicateKeyException concurrentInsert) {
                markBackfillProgress(
                        memberId, channelId, eventId, lastMessageId,
                        scanCursor, scanAnchorMessageId, polledAt);
            }
        }
    }

    @Override
    public void markFailure(
            String memberId,
            String channelId,
            String eventId,
            String errorCode,
            Instant polledAt) {
        String safeCode = errorCode == null ? "UNKNOWN" : errorCode.substring(0, Math.min(120, errorCode.length()));
        int updated = jdbc.update("""
                UPDATE chat_polling_cursors
                SET last_message_id = CASE WHEN event_id = ? THEN last_message_id ELSE NULL END,
                    scan_cursor = CASE WHEN event_id = ? THEN scan_cursor ELSE NULL END,
                    scan_anchor_message_id = CASE
                        WHEN event_id = ? THEN scan_anchor_message_id ELSE NULL END,
                    event_id = ?, last_polled_at = ?, last_error_code = ?
                WHERE member_id = ? AND channel_id = ?
                  AND (last_polled_at IS NULL OR last_polled_at <= ?)
                """, eventId, eventId, eventId, eventId, timestamp(polledAt), safeCode,
                memberId, channelId, timestamp(polledAt));
        if (updated == 0 && find(memberId, channelId).isEmpty()) {
            try {
                jdbc.update("""
                        INSERT INTO chat_polling_cursors (
                            member_id, channel_id, event_id, last_message_id,
                            scan_cursor, scan_anchor_message_id, last_polled_at,
                            last_success_at, last_error_code)
                        VALUES (?, ?, ?, NULL, NULL, NULL, ?, NULL, ?)
                        """, memberId, channelId, eventId, timestamp(polledAt), safeCode);
            } catch (DuplicateKeyException concurrentInsert) {
                markFailure(memberId, channelId, eventId, safeCode, polledAt);
            }
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static final class CursorRowMapper implements RowMapper<ChatPollingCursor> {
        @Override
        public ChatPollingCursor mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ChatPollingCursor(
                    rs.getString("member_id"),
                    rs.getString("channel_id"),
                    rs.getString("event_id"),
                    rs.getString("last_message_id"),
                    rs.getString("scan_cursor"),
                    rs.getString("scan_anchor_message_id"),
                    instant(rs.getTimestamp("last_polled_at")),
                    instant(rs.getTimestamp("last_success_at")),
                    rs.getString("last_error_code"));
        }
    }
}
