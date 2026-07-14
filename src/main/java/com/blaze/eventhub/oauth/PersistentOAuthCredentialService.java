package com.blaze.eventhub.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.blaze.eventhub.common.ConfigurationMissingException;
import com.blaze.eventhub.config.BlazeProperties;

@Service
public class PersistentOAuthCredentialService {

    private static final long REFRESH_SKEW_SECONDS = 60;

    private final OAuthCredentialStore credentialStore;
    private final BlazeOAuthGateway gateway;
    private final BlazeProperties properties;
    private final Clock clock;

    public PersistentOAuthCredentialService(
            OAuthCredentialStore credentialStore,
            BlazeOAuthGateway gateway,
            BlazeProperties properties,
            Clock clock) {
        this.credentialStore = credentialStore;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized TokenSnapshot currentValid(String memberId) {
        TokenSnapshot current = credentialStore.findByMemberId(memberId)
                .orElseThrow(() -> new ConfigurationMissingException(
                        "Credencial OAuth persistente nao encontrada para o criador"));
        if (current.accessTokenBlank()) {
            throw new ConfigurationMissingException("Credencial OAuth persistente sem access token");
        }

        Instant refreshThreshold = Instant.now(clock).plusSeconds(REFRESH_SKEW_SECONDS);
        if (current.expiresAt() != null && current.expiresAt().isAfter(refreshThreshold)) {
            return current;
        }
        if (current.refreshTokenBlank()) {
            throw new ConfigurationMissingException(
                    "Credencial OAuth expirada e sem refresh token; reconecte a conta Blaze");
        }
        if (!StringUtils.hasText(properties.getClientId()) || !StringUtils.hasText(properties.getClientSecret())) {
            throw new ConfigurationMissingException("Blaze OAuth is not configured");
        }

        OAuthTokenResponse refreshed = gateway.refresh(new OAuthRefreshRequest(
                properties.getClientId(),
                properties.getClientSecret(),
                current.refreshToken()));
        if (refreshed == null || !StringUtils.hasText(refreshed.accessToken())) {
            throw new IllegalStateException("Blaze OAuth refresh nao retornou access token");
        }

        Instant now = Instant.now(clock);
        TokenSnapshot updated = new TokenSnapshot(
                textOr(refreshed.type(), current.type()),
                textOr(refreshed.userId(), current.userId()),
                textOr(refreshed.tokenType(), current.tokenType()),
                refreshed.accessToken(),
                textOr(refreshed.refreshToken(), current.refreshToken()),
                refreshed.expiresIn() == null ? null : now.plusSeconds(Math.max(0, refreshed.expiresIn())),
                scopesOr(refreshed.scopes(), current.scopes()),
                now);
        credentialStore.save(memberId, updated);
        return updated;
    }

    private static String textOr(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static List<String> scopesOr(List<String> preferred, List<String> fallback) {
        return preferred == null || preferred.isEmpty()
                ? (fallback == null ? List.of() : fallback)
                : preferred;
    }
}
