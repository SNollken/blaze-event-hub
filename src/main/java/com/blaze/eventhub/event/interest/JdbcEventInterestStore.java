package com.blaze.eventhub.event.interest;

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
public class JdbcEventInterestStore implements EventInterestStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<EventInterest> ROW_MAPPER = new InterestRowMapper();

    public JdbcEventInterestStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EventInterest save(EventInterest interest) {
        jdbc.update("""
                MERGE INTO event_interests (id, event_id, member_id, status, interested_at,
                    last_calculated_entries, notes, created_at, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                interest.id(),
                interest.eventId(),
                interest.memberId(),
                interest.status().name().toLowerCase(),
                toTimestamp(interest.interestedAt()),
                interest.lastCalculatedEntries(),
                interest.notes(),
                toTimestamp(interest.createdAt()),
                toTimestamp(interest.updatedAt()));
        return interest;
    }

    @Override
    public Optional<EventInterest> findByEventIdAndMemberId(String eventId, String memberId) {
        List<EventInterest> result = jdbc.query(
                "SELECT * FROM event_interests WHERE event_id = ? AND member_id = ?",
                ROW_MAPPER, eventId, memberId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<EventInterest> findByEventId(String eventId) {
        return jdbc.query(
                "SELECT * FROM event_interests WHERE event_id = ? ORDER BY interested_at ASC",
                ROW_MAPPER, eventId);
    }

    @Override
    public List<EventInterest> findByMemberId(String memberId) {
        return jdbc.query(
                "SELECT * FROM event_interests WHERE member_id = ? ORDER BY interested_at DESC",
                ROW_MAPPER, memberId);
    }

    @Override
    public boolean existsByEventIdAndMemberId(String eventId, String memberId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_interests WHERE event_id = ? AND member_id = ?",
                Integer.class, eventId, memberId);
        return count != null && count > 0;
    }

    @Override
    public int delete(String eventId, String memberId) {
        return jdbc.update("DELETE FROM event_interests WHERE event_id = ? AND member_id = ?",
                eventId, memberId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class InterestRowMapper implements RowMapper<EventInterest> {
        @Override
        public EventInterest mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EventInterest(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    rs.getString("member_id"),
                    InterestStatus.fromDb(rs.getString("status")),
                    toInstant(rs.getTimestamp("interested_at")),
                    rs.getInt("last_calculated_entries"),
                    rs.getString("notes"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
