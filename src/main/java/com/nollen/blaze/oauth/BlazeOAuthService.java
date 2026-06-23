package com.nollen.blaze.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.nollen.blaze.common.ConfigurationMissingException;
import com.nollen.blaze.config.BlazeProperties;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BlazeOAuthService {

	private final BlazeProperties properties;
	private final BlazeOAuthGateway gateway;
	private final OAuthStateStore stateStore;
	private final TokenStore tokenStore;
	private final Clock clock;

	public BlazeOAuthService(BlazeProperties properties, BlazeOAuthGateway gateway, OAuthStateStore stateStore,
			TokenStore tokenStore, Clock clock) {
		this.properties = properties;
		this.gateway = gateway;
		this.stateStore = stateStore;
		this.tokenStore = tokenStore;
		this.clock = clock;
	}

	public OAuthStartResponse start() {
		requireOAuthConfiguration();
		GeneratedAuthUrl generated = gateway.generateAuthUrl(new OAuthGenerateAuthUrlRequest(
				properties.getClientId(),
				properties.getClientSecret(),
				properties.getRedirectUri(),
				properties.getScopes()));
		if (!StringUtils.hasText(generated.authorizationUrl()) || !StringUtils.hasText(generated.state())
				|| !StringUtils.hasText(generated.codeVerifier())) {
			throw new IllegalStateException("Blaze OAuth generate-auth-url returned an incomplete response");
		}
		stateStore.save(new OAuthState(generated.state(), generated.codeVerifier(), Instant.now(clock)));
		return new OAuthStartResponse(generated.authorizationUrl(), generated.state(), properties.getScopes());
	}

	public OAuthCallbackResponse callback(String code, String state) {
		requireOAuthConfiguration();
		if (!StringUtils.hasText(code)) {
			throw new IllegalArgumentException("OAuth callback code is required");
		}
		OAuthState stored = stateStore.consume(state)
				.orElseThrow(() -> new IllegalArgumentException("Invalid OAuth state"));
		OAuthTokenResponse response = gateway.exchangeCode(new OAuthTokenExchangeRequest(
				properties.getClientId(),
				properties.getClientSecret(),
				code,
				stored.codeVerifier(),
				properties.getRedirectUri(),
				"authorization_code"));
		return saveAndSanitize(response);
	}

	public OAuthCallbackResponse refresh() {
		requireOAuthConfiguration();
		TokenSnapshot current = tokenStore.current()
				.filter(token -> !token.refreshTokenBlank())
				.orElseThrow(() -> new ConfigurationMissingException("No refresh token is available"));
		OAuthTokenResponse response = gateway.refresh(new OAuthRefreshRequest(
				properties.getClientId(),
				properties.getClientSecret(),
				current.refreshToken()));
		return saveAndSanitize(response);
	}

	private OAuthCallbackResponse saveAndSanitize(OAuthTokenResponse response) {
		if (!StringUtils.hasText(response.accessToken())) {
			throw new IllegalStateException("Blaze OAuth token response did not include an access token");
		}
		Instant expiresAt = response.expiresIn() == null
				? null
				: Instant.now(clock).plusSeconds(Math.max(response.expiresIn(), 0));
		TokenSnapshot snapshot = new TokenSnapshot(
				response.type(),
				response.userId(),
				response.tokenType() == null ? "Bearer" : response.tokenType(),
				response.accessToken(),
				response.refreshToken(),
				expiresAt,
				response.scopes() == null ? List.of() : response.scopes(),
				Instant.now(clock));
		tokenStore.save(snapshot);
		return new OAuthCallbackResponse(
				"stored",
				snapshot.tokenType(),
				snapshot.userId(),
				snapshot.scopes(),
				snapshot.expiresAt(),
				!snapshot.refreshTokenBlank());
	}

	private void requireOAuthConfiguration() {
		if (!properties.isOAuthConfigured()) {
			throw new ConfigurationMissingException("Blaze OAuth is not configured");
		}
	}
}
