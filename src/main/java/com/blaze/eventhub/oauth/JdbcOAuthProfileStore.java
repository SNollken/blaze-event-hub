package com.blaze.eventhub.oauth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed OAuth profile store. Survives restarts and deploys.
 * Single-row table (id=1).
 */
@Repository
public class JdbcOAuthProfileStore implements OAuthProfileStore {

	private static final Logger log = LoggerFactory.getLogger(JdbcOAuthProfileStore.class);

	private final JdbcTemplate jdbc;

	public JdbcOAuthProfileStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<OAuthProfileSummary> current() {
		try {
			OAuthProfileSummary profile = jdbc.queryForObject(
				"SELECT user_id, username, display_name, avatar_url FROM oauth_profile WHERE id = 1",
				(rs, rowNum) -> new OAuthProfileSummary(
					rs.getString("user_id"),
					rs.getString("username"),
					rs.getString("display_name"),
					rs.getString("avatar_url"),
					rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now()),
				(Object[]) null);
			return Optional.ofNullable(profile);
		} catch (EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}

	@Override
	public void save(OAuthProfileSummary profile) {
		int updated = jdbc.update(
			"UPDATE oauth_profile SET user_id = ?, username = ?, display_name = ?, avatar_url = ?, updated_at = ? WHERE id = 1",
			profile.id(), profile.username(), profile.displayName(), profile.avatarUrl(),
			profile.syncedAt() != null ? Timestamp.from(profile.syncedAt()) : null);
		if (updated == 0) {
			jdbc.update(
				"INSERT INTO oauth_profile (id, user_id, username, display_name, avatar_url, updated_at) VALUES (1, ?, ?, ?, ?, ?)",
				profile.id(), profile.username(), profile.displayName(), profile.avatarUrl(),
				profile.syncedAt() != null ? Timestamp.from(profile.syncedAt()) : null);
		}
		log.debug("OAuth profile persisted for user {}", profile.username());
	}

	@Override
	public void clear() {
		jdbc.update("DELETE FROM oauth_profile WHERE id = 1");
		log.debug("OAuth profile cleared");
	}
}
