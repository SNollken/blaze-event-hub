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
                        source_message_id, entered_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    participant.id(),
                    participant.eventId(),
                    participant.blazeUserId(),
                    participant.blazeUsername(),
                    participant.displayName(),
                    participant.sourceMessageId(),
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
                    rs.getTimestamp("entered_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant());
        }
    }
}
