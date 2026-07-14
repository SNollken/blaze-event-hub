package com.blaze.eventhub.member;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMemberStore implements MemberStore {

	private final JdbcTemplate jdbc;

	public JdbcMemberStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Member save(Member member) {
		int updated = updateExisting(member);
		if (updated == 0) {
			try {
				jdbc.update("""
						INSERT INTO members (id, blaze_user_id, blaze_username, display_name, avatar_url,
							status, created_at, updated_at)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?)
						""",
						member.id(),
						member.blazeUserId(),
						member.blazeUsername(),
						member.displayName(),
						member.avatarUrl(),
						member.status(),
						toTimestamp(member.createdAt()),
						toTimestamp(member.updatedAt()));
			} catch (DuplicateKeyException concurrentInsert) {
				updateExisting(member);
			}
		}
		return findByBlazeUserId(member.blazeUserId()).orElseThrow();
	}

	private int updateExisting(Member member) {
		return jdbc.update("""
					UPDATE members SET
						blaze_username = ?,
						display_name = ?,
						avatar_url = ?,
						status = ?,
						updated_at = ?
					WHERE blaze_user_id = ?
					""",
					member.blazeUsername(),
					member.displayName(),
					member.avatarUrl(),
					member.status(),
					toTimestamp(member.updatedAt()),
					member.blazeUserId());
	}

	@Override
	public Optional<Member> findById(String id) {
		return querySingle("SELECT * FROM members WHERE id = ?", id);
	}

	@Override
	public Optional<Member> findByBlazeUserId(String blazeUserId) {
		return querySingle("SELECT * FROM members WHERE blaze_user_id = ?", blazeUserId);
	}

	@Override
	public boolean existsByBlazeUserId(String blazeUserId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM members WHERE blaze_user_id = ?",
				Integer.class,
				blazeUserId);
		return count != null && count > 0;
	}

	private Optional<Member> querySingle(String sql, Object... args) {
		var list = jdbc.query(sql, new MemberRowMapper(), args);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
	}

	private static Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static class MemberRowMapper implements RowMapper<Member> {
		@Override
		public Member mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new Member(
					rs.getString("id"),
					rs.getString("blaze_user_id"),
					rs.getString("blaze_username"),
					rs.getString("display_name"),
					rs.getString("avatar_url"),
					rs.getString("status"),
					toInstant(rs.getTimestamp("created_at")),
					toInstant(rs.getTimestamp("updated_at")));
		}
	}
}
