package com.blaze.eventhub.event.participant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventParticipantStore implements EventParticipantStore {

    private static final RowMapper<EventParticipant> ROW_MAPPER = new ParticipantRowMapper();

    private final JdbcTemplate jdbc;

    public JdbcEventParticipantStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfAbsent(EventParticipant participant) {
        try {
            return jdbc.update("""
                    INSERT INTO event_participants (
                        id, event_id, blaze_user_id, blaze_username, display_name,
                        source_message_id, action_type, entry_weight, raw_action_count, entered_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    participant.id(),
                    participant.eventId(),
                    participant.blazeUserId(),
                    participant.blazeUsername(),
                    participant.displayName(),
                    participant.sourceMessageId(),
                    participant.actionType() != null ? participant.actionType() : "chat",
                    participant.entryWeight() > 0 ? participant.entryWeight() : 1,
                    participant.rawActionCount(),
                    Timestamp.from(participant.enteredAt()),
                    Timestamp.from(participant.createdAt())) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public List<EventParticipant> findByEventId(String eventId) {
        return jdbc.query("""
                SELECT * FROM event_participants
                WHERE event_id = ?
                ORDER BY entered_at, id
                """, ROW_MAPPER, eventId);
    }

    @Override
    public int countByEventId(String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_participants WHERE event_id = ?",
                Integer.class,
                eventId);
        return count != null ? count : 0;
    }

    @Override
    public int getRawActionCount(String eventId, String blazeUserId, String actionType) {
        Integer count = jdbc.queryForObject(
                "SELECT raw_action_count FROM event_participants WHERE event_id = ? AND blaze_user_id = ? AND action_type = ?",
                Integer.class,
                eventId, blazeUserId, actionType);
        return count != null ? count : 0;
    }

    @Override
    public void incrementRawActionCount(String eventId, String blazeUserId, String actionType) {
        jdbc.update(
                "UPDATE event_participants SET raw_action_count = raw_action_count + 1 WHERE event_id = ? AND blaze_user_id = ? AND action_type = ?",
                eventId, blazeUserId, actionType);
    }

    @Override
    public void updateEntryWeight(String eventId, String blazeUserId, int newWeight) {
        jdbc.update(
                "UPDATE event_participants SET entry_weight = ? WHERE event_id = ? AND blaze_user_id = ?",
                newWeight, eventId, blazeUserId);
    }

    private static final class ParticipantRowMapper implements RowMapper<EventParticipant> {
        @Override
        public EventParticipant mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EventParticipant(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    rs.getString("blaze_user_id"),
                    rs.getString("blaze_username"),
                    rs.getString("display_name"),
                    rs.getString("source_message_id"),
                    rs.getString("action_type") != null ? rs.getString("action_type") : "chat",
                    rs.getInt("entry_weight"),
                    rs.getInt("raw_action_count"),
                    rs.getTimestamp("entered_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant());
        }
    }
}