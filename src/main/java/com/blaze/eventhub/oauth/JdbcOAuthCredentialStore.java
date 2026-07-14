package com.blaze.eventhub.oauth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcOAuthCredentialStore implements OAuthCredentialStore {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final AesGcmCredentialCipher cipher;
    private final ObjectMapper objectMapper;

    public JdbcOAuthCredentialStore(
            JdbcTemplate jdbc,
            AesGcmCredentialCipher cipher,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String memberId, TokenSnapshot token) {
        String accessCiphertext = cipher.encrypt(
                token.accessToken(), credentialContext(memberId, token.userId(), "access"));
        String refreshCiphertext = cipher.encrypt(
                token.refreshToken(), credentialContext(memberId, token.userId(), "refresh"));
        String scopesJson = writeScopes(token.scopes());

        int updated = jdbc.update("""
                UPDATE oauth_credentials
                SET token_subject_type = ?, blaze_user_id = ?, token_type = ?,
                    access_token_ciphertext = ?, refresh_token_ciphertext = ?,
                    expires_at = ?, scopes_json = ?, updated_at = ?
                WHERE member_id = ?
                """,
                token.type(),
                token.userId(),
                token.tokenType(),
                accessCiphertext,
                refreshCiphertext,
                timestamp(token.expiresAt()),
                scopesJson,
                timestamp(token.updatedAt()),
                memberId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO oauth_credentials (
                        member_id, token_subject_type, blaze_user_id, token_type,
                        access_token_ciphertext, refresh_token_ciphertext,
                        expires_at, scopes_json, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    memberId,
                    token.type(),
                    token.userId(),
                    token.tokenType(),
                    accessCiphertext,
                    refreshCiphertext,
                    timestamp(token.expiresAt()),
                    scopesJson,
                    timestamp(token.updatedAt()));
        }
    }

    @Override
    public Optional<TokenSnapshot> findByMemberId(String memberId) {
        List<TokenSnapshot> matches = jdbc.query("""
                SELECT * FROM oauth_credentials WHERE member_id = ?
                """, new CredentialRowMapper(), memberId);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    @Override
    public void deleteByMemberId(String memberId) {
        jdbc.update("DELETE FROM oauth_credentials WHERE member_id = ?", memberId);
    }

    @Override
    public void deleteByBlazeUserId(String blazeUserId) {
        jdbc.update("DELETE FROM oauth_credentials WHERE blaze_user_id = ?", blazeUserId);
    }

    private String writeScopes(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes == null ? List.of() : scopes);
        } catch (JsonProcessingException impossible) {
            throw new IllegalArgumentException("Escopos OAuth invalidos", impossible);
        }
    }

    private List<String> readScopes(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException invalidData) {
            throw new DataRetrievalFailureException("Escopos OAuth persistidos sao invalidos", invalidData);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String credentialContext(String memberId, String blazeUserId, String tokenKind) {
        if (memberId == null || memberId.isBlank() || blazeUserId == null || blazeUserId.isBlank()) {
            throw new IllegalArgumentException("Identidade da credencial OAuth e obrigatoria");
        }
        return memberId.length() + ":" + memberId
                + ":" + blazeUserId.length() + ":" + blazeUserId
                + ":" + tokenKind;
    }

    private final class CredentialRowMapper implements RowMapper<TokenSnapshot> {
        @Override
        public TokenSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            String memberId = rs.getString("member_id");
            String blazeUserId = rs.getString("blaze_user_id");
            return new TokenSnapshot(
                    rs.getString("token_subject_type"),
                    blazeUserId,
                    rs.getString("token_type"),
                    cipher.decrypt(
                            rs.getString("access_token_ciphertext"),
                            credentialContext(memberId, blazeUserId, "access")),
                    cipher.decrypt(
                            rs.getString("refresh_token_ciphertext"),
                            credentialContext(memberId, blazeUserId, "refresh")),
                    instant(rs.getTimestamp("expires_at")),
                    readScopes(rs.getString("scopes_json")),
                    instant(rs.getTimestamp("updated_at")));
        }
    }
}
