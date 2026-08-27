package com.blaze.eventhub.oauth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed token store. Survives restarts and deploys.
 * Single-row table (id=1) — only one Blaze account connected at a time.
 */
@Repository
public class JdbcTokenStore implements TokenStore {

	private static final Logger log = LoggerFactory.getLogger(JdbcTokenStore.class);

	private final JdbcTemplate jdbc;

	public JdbcTokenStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<TokenSnapshot> current() {
		try {
			TokenSnapshot snapshot = jdbc.queryForObject(
				"SELECT type, user_id, token_type, access_token, refresh_token, expires_at, scopes, updated_at FROM oauth_token WHERE id = 1",
				(rs, rowNum) -> new TokenSnapshot(
					rs.getString("type"),
					rs.getString("user_id"),
					rs.getString("token_type"),
					rs.getString("access_token"),
					rs.getString("refresh_token"),
					rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant() : null,
					parseScopes(rs.getString("scopes")),
					rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null),
				(Object[]) null);
			return Optional.ofNullable(snapshot);
		} catch (EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}

	@Override
	public void save(TokenSnapshot tokenSnapshot) {
		int updated = jdbc.update(
			"UPDATE oauth_token SET type = ?, user_id = ?, token_type = ?, access_token = ?, refresh_token = ?, expires_at = ?, scopes = ?, updated_at = ? WHERE id = 1",
			tokenSnapshot.type(),
			tokenSnapshot.userId(),
			tokenSnapshot.tokenType(),
			tokenSnapshot.accessToken(),
			tokenSnapshot.refreshToken(),
			tokenSnapshot.expiresAt() != null ? Timestamp.from(tokenSnapshot.expiresAt()) : null,
			joinScopes(tokenSnapshot.scopes()),
			tokenSnapshot.updatedAt() != null ? Timestamp.from(tokenSnapshot.updatedAt()) : null);
		if (updated == 0) {
			jdbc.update(
				"INSERT INTO oauth_token (id, type, user_id, token_type, access_token, refresh_token, expires_at, scopes, updated_at) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)",
				tokenSnapshot.type(),
				tokenSnapshot.userId(),
				tokenSnapshot.tokenType(),
				tokenSnapshot.accessToken(),
				tokenSnapshot.refreshToken(),
				tokenSnapshot.expiresAt() != null ? Timestamp.from(tokenSnapshot.expiresAt()) : null,
				joinScopes(tokenSnapshot.scopes()),
				tokenSnapshot.updatedAt() != null ? Timestamp.from(tokenSnapshot.updatedAt()) : null);
		}
		log.debug("OAuth token persisted for user {}", tokenSnapshot.userId());
	}

	@Override
	public void clear() {
		jdbc.update("DELETE FROM oauth_token WHERE id = 1");
		log.debug("OAuth token cleared");
	}

	private List<String> parseScopes(String scopes) {
		if (scopes == null || scopes.isBlank()) {
			return List.of();
		}
		return Arrays.asList(scopes.split(","));
	}

	private String joinScopes(List<String> scopes) {
		if (scopes == null || scopes.isEmpty()) {
			return null;
		}
		return String.join(",", scopes);
	}
}
