package com.blaze.eventhub.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OAuthCredentialStoreIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OAuthCredentialStore credentialStore;

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM oauth_credentials WHERE member_id = 'member-1'");
        jdbc.update("DELETE FROM members WHERE id = 'member-1'");
        jdbc.update("""
                INSERT INTO members (id, blaze_user_id, blaze_username, display_name, status, created_at, updated_at)
                VALUES ('member-1', 'blaze-user-1', 'creator', 'Creator', 'active', NOW(), NOW())
                """);
    }

    @Test
    void storesOnlyCiphertextAndRestoresCompleteTokenSnapshot() {
        TokenSnapshot token = new TokenSnapshot(
                "user",
                "blaze-user-1",
                "Bearer",
                "access-secret",
                "refresh-secret",
                Instant.parse("2026-07-15T12:00:00Z"),
                List.of("users.read", "offline.access"),
                Instant.parse("2026-07-14T12:00:00Z"));

        credentialStore.save("member-1", token);

        String rawAccess = jdbc.queryForObject(
                "SELECT access_token_ciphertext FROM oauth_credentials WHERE member_id = 'member-1'",
                String.class);
        assertTrue(rawAccess.startsWith("v2."));
        assertFalse(rawAccess.contains("access-secret"));

        TokenSnapshot restored = credentialStore.findByMemberId("member-1").orElseThrow();
        assertEquals(token, restored);

        credentialStore.deleteByMemberId("member-1");
        assertTrue(credentialStore.findByMemberId("member-1").isEmpty());
    }
}
