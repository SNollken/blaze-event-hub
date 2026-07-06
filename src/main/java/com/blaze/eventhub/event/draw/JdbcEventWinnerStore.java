package com.blaze.eventhub.event.draw;

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
public class JdbcEventWinnerStore implements EventWinnerStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<EventWinner> ROW_MAPPER = new WinnerRowMapper();

    public JdbcEventWinnerStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EventWinner save(EventWinner winner) {
        jdbc.update("""
                MERGE INTO event_winners (id, event_id, member_id, entries_at_draw_time,
                    draw_seed, draw_method, selected_at, selected_by, notes)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                winner.id(),
                winner.eventId(),
                winner.memberId(),
                winner.entriesAtDrawTime(),
                winner.drawSeed(),
                winner.drawMethod(),
                toTimestamp(winner.selectedAt()),
                winner.selectedBy(),
                winner.notes());
        return winner;
    }

    @Override
    public Optional<EventWinner> findByEventId(String eventId) {
        List<EventWinner> result = jdbc.query(
                "SELECT * FROM event_winners WHERE event_id = ? ORDER BY selected_at DESC",
                ROW_MAPPER, eventId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<EventWinner> findAllByEventId(String eventId) {
        return jdbc.query(
                "SELECT * FROM event_winners WHERE event_id = ? ORDER BY selected_at DESC",
                ROW_MAPPER, eventId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class WinnerRowMapper implements RowMapper<EventWinner> {
        @Override
        public EventWinner mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EventWinner(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    rs.getString("member_id"),
                    rs.getInt("entries_at_draw_time"),
                    rs.getString("draw_seed"),
                    rs.getString("draw_method"),
                    toInstant(rs.getTimestamp("selected_at")),
                    rs.getString("selected_by"),
                    rs.getString("notes"));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
