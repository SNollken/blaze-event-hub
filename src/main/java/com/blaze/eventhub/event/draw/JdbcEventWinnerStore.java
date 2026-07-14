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
                INSERT INTO event_draw_results (
                    id, event_id, winner_blaze_user_id, winner_username, winner_display_name,
                    draw_seed, draw_method, pool_hash, participant_count, selected_at, selected_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                winner.id(),
                winner.eventId(),
                winner.winnerBlazeUserId(),
                winner.winnerUsername(),
                winner.winnerDisplayName(),
                winner.drawSeed(),
                winner.drawMethod(),
                winner.poolHash(),
                winner.participantCount(),
                toTimestamp(winner.selectedAt()),
                winner.selectedBy());
        return winner;
    }

    @Override
    public Optional<EventWinner> findByEventId(String eventId) {
        List<EventWinner> result = jdbc.query(
                "SELECT * FROM event_draw_results WHERE event_id = ?",
                ROW_MAPPER, eventId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<EventWinner> findAllByEventId(String eventId) {
        return jdbc.query(
                "SELECT * FROM event_draw_results WHERE event_id = ?",
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
                    rs.getString("winner_blaze_user_id"),
                    rs.getString("winner_username"),
                    rs.getString("winner_display_name"),
                    rs.getString("draw_seed"),
                    rs.getString("draw_method"),
                    rs.getString("pool_hash"),
                    rs.getInt("participant_count"),
                    toInstant(rs.getTimestamp("selected_at")),
                    rs.getString("selected_by"));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
