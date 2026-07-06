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
    public Event save(Event event) {
        jdbc.update("""
                MERGE INTO events (id, creator_member_id, creator_blaze_user_id, creator_channel_id,
                    title, description, prize_type, prize_description, status, rules_mode,
                    max_entries_per_participant, requires_interest_before_action,
                    starts_at, ends_at, created_at, updated_at, closed_at, completed_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.id(),
                event.creatorMemberId(),
                event.creatorBlazeUserId(),
                event.creatorChannelId(),
                event.title(),
                event.description(),
                event.prizeType(),
                event.prizeDescription(),
                event.status().name().toLowerCase(),
                event.rulesMode().name().toLowerCase(),
                event.maxEntriesPerParticipant(),
                event.requiresInterestBeforeAction(),
                toTimestamp(event.startsAt()),
                toTimestamp(event.endsAt()),
                toTimestamp(event.createdAt()),
                toTimestamp(event.updatedAt()),
                toTimestamp(event.closedAt()),
                toTimestamp(event.completedAt()));
        return event;
    }

    @Override
    public Optional<Event> findById(String id) {
        List<Event> result = jdbc.query("SELECT * FROM events WHERE id = ?", ROW_MAPPER, id);
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
    public List<Event> findAll() {
        return jdbc.query("SELECT * FROM events ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public int updateStatus(String id, EventStatus newStatus) {
        Instant now = Instant.now();
        return jdbc.update("UPDATE events SET status = ?, updated_at = ? WHERE id = ?",
                newStatus.name().toLowerCase(), Timestamp.from(now), id);
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
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("prize_type"),
                    rs.getString("prize_description"),
                    EventStatus.fromDb(rs.getString("status")),
                    RulesMode.fromDb(rs.getString("rules_mode")),
                    rs.getInt("max_entries_per_participant"),
                    rs.getBoolean("requires_interest_before_action"),
                    toInstant(rs.getTimestamp("starts_at")),
                    toInstant(rs.getTimestamp("ends_at")),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")),
                    toInstant(rs.getTimestamp("closed_at")),
                    toInstant(rs.getTimestamp("completed_at")));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
