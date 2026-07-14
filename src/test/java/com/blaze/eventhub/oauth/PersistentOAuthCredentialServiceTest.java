package com.blaze.eventhub.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blaze.eventhub.common.ConfigurationMissingException;
import com.blaze.eventhub.config.BlazeProperties;

class PersistentOAuthCredentialServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");

    private OAuthCredentialStore store;
    private BlazeOAuthGateway gateway;
    private PersistentOAuthCredentialService service;

    @BeforeEach
    void setUp() {
        store = mock(OAuthCredentialStore.class);
        gateway = mock(BlazeOAuthGateway.class);
        BlazeProperties properties = new BlazeProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        service = new PersistentOAuthCredentialService(
                store,
                gateway,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsFreshCredentialWithoutRefreshing() {
        TokenSnapshot fresh = token("access-1", "refresh-1", NOW.plusSeconds(3600));
        when(store.findByMemberId("member-1")).thenReturn(Optional.of(fresh));

        TokenSnapshot result = service.currentValid("member-1");

        assertSame(fresh, result);
        verify(gateway, never()).refresh(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshesExpiringCredentialAndPreservesRotatingFields() {
        TokenSnapshot expired = token("access-1", "refresh-1", NOW.minusSeconds(1));
        when(store.findByMemberId("member-1")).thenReturn(Optional.of(expired));
        when(gateway.refresh(org.mockito.ArgumentMatchers.any())).thenReturn(
                new OAuthTokenResponse(null, null, "Bearer", "access-2", null, 3600L, null));

        TokenSnapshot result = service.currentValid("member-1");

        assertEquals("access-2", result.accessToken());
        assertEquals("refresh-1", result.refreshToken());
        assertEquals("blaze-user-1", result.userId());
        assertEquals(List.of("users.read", "offline.access"), result.scopes());
        assertEquals(NOW.plusSeconds(3600), result.expiresAt());
        verify(store).save("member-1", result);
    }

    @Test
    void rejectsMissingPersistentCredential() {
        when(store.findByMemberId("member-1")).thenReturn(Optional.empty());

        assertThrows(ConfigurationMissingException.class, () -> service.currentValid("member-1"));
    }

    private static TokenSnapshot token(String accessToken, String refreshToken, Instant expiresAt) {
        return new TokenSnapshot(
                "user",
                "blaze-user-1",
                "Bearer",
                accessToken,
                refreshToken,
                expiresAt,
                List.of("users.read", "offline.access"),
                NOW.minusSeconds(60));
    }
}
