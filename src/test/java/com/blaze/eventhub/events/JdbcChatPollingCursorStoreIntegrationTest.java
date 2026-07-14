package com.blaze.eventhub.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JdbcChatPollingCursorStoreIntegrationTest {

    private static final String MEMBER_ID = "cursor-member";
    private static final String CHANNEL_ID = "cursor-channel";
    private static final Instant FIRST_POLL = Instant.parse("2026-07-14T12:00:00Z");

    @Autowired
    private JdbcChatPollingCursorStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM chat_polling_cursors WHERE member_id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM events WHERE creator_member_id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM members WHERE id = ?", MEMBER_ID);
        jdbc.update("""
                INSERT INTO members (
                    id, blaze_user_id, blaze_username, display_name,
                    status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, MEMBER_ID, "cursor-blaze-user", "cursor-user", "Cursor User");
        jdbc.update("""
                INSERT INTO events (
                    id, creator_member_id, creator_blaze_user_id, creator_channel_id,
                    title, prize, entry_command, status, created_at, updated_at)
                VALUES
                    ('event-1', ?, 'cursor-blaze-user', ?, 'Evento 1', 'Premio', '!entrar',
                        'draft', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('event-old', ?, 'cursor-blaze-user', ?, 'Evento antigo', 'Premio', '!entrar',
                        'draft', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('event-new', ?, 'cursor-blaze-user', ?, 'Evento novo', 'Premio', '!entrar',
                        'draft', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, MEMBER_ID, CHANNEL_ID, MEMBER_ID, CHANNEL_ID, MEMBER_ID, CHANNEL_ID);
    }

    @Test
    void persistsResumesAndCompletesBackfillWithoutPromotingEarly() {
        store.markBackfillProgress(
                MEMBER_ID,
                CHANNEL_ID,
                "event-1",
                "boundary-1",
                "older-page",
                "newest-anchor",
                FIRST_POLL);

        ChatPollingCursor pending = store.find(MEMBER_ID, CHANNEL_ID).orElseThrow();
        assertEquals("event-1", pending.eventId());
        assertEquals("boundary-1", pending.lastMessageId());
        assertEquals("older-page", pending.scanCursor());
        assertEquals("newest-anchor", pending.scanAnchorMessageId());
        assertEquals("CHAT_BACKFILL_PENDING", pending.lastErrorCode());
        assertNull(pending.lastSuccessAt());

        Instant transientFailureAt = FIRST_POLL.plusSeconds(1);
        store.markFailure(
                MEMBER_ID,
                CHANNEL_ID,
                "event-1",
                "BLAZE_HTTP_503",
                transientFailureAt);
        ChatPollingCursor failed = store.find(MEMBER_ID, CHANNEL_ID).orElseThrow();
        assertEquals("older-page", failed.scanCursor());
        assertEquals("newest-anchor", failed.scanAnchorMessageId());
        assertEquals("BLAZE_HTTP_503", failed.lastErrorCode());

        Instant completedAt = transientFailureAt.plusSeconds(1);
        store.markSuccess(
                MEMBER_ID,
                CHANNEL_ID,
                "event-1",
                "newest-anchor",
                completedAt);
        ChatPollingCursor completed = store.find(MEMBER_ID, CHANNEL_ID).orElseThrow();
        assertEquals("newest-anchor", completed.lastMessageId());
        assertNull(completed.scanCursor());
        assertNull(completed.scanAnchorMessageId());
        assertNull(completed.lastErrorCode());
        assertEquals(completedAt, completed.lastSuccessAt());
    }

    @Test
    void failureOnANewEventClearsThePreviousEventsCursorState() {
        store.markBackfillProgress(
                MEMBER_ID,
                CHANNEL_ID,
                "event-old",
                "old-boundary",
                "old-page",
                "old-anchor",
                FIRST_POLL);

        store.markFailure(
                MEMBER_ID,
                CHANNEL_ID,
                "event-new",
                "BLAZE_HTTP_503",
                FIRST_POLL.plusSeconds(1));

        ChatPollingCursor current = store.find(MEMBER_ID, CHANNEL_ID).orElseThrow();
        assertEquals("event-new", current.eventId());
        assertNull(current.lastMessageId());
        assertNull(current.scanCursor());
        assertNull(current.scanAnchorMessageId());
        assertEquals("BLAZE_HTTP_503", current.lastErrorCode());
    }
}
