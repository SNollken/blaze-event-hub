package com.nollen.blaze.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.nollen.blaze.common.ConfigurationMissingException;
import com.nollen.blaze.common.OAuthException;
import com.nollen.blaze.config.BlazeProperties;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

@Service
public class BlazeOAuthService {

	private final BlazeProperties properties;
	private final BlazeOAuthGateway gateway;
	private final OAuthStateStore stateStore;
	private final TokenStore tokenStore;
	private final OAuthProfileService profileService;
	private final Clock clock;
	// ponytail: lock local por instância; em multi-instância trocar por Redis/DistLock.
	private final ReentrantLock refreshLock = new ReentrantLock();

	public BlazeOAuthService(BlazeProperties properties, BlazeOAuthGateway gateway, OAuthStateStore stateStore,
			TokenStore tokenStore, OAuthProfileService profileService, Clock clock) {
		this.properties = properties;
		this.gateway = gateway;
		this.stateStore = stateStore;
		this.tokenStore = tokenStore;
		this.profileService = profileService;
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
		return new OAuthStartResponse(generated.authorizationUrl(), properties.getScopes());
	}

	public OAuthCallbackResponse callback(String code, String state, String error, String errorDescription) {
		// Blaze rejeitou a autorizacao
		if (error != null && !error.isBlank()) {
			String desc = errorDescription != null && !errorDescription.isBlank()
					? errorDescription
					: "OAuth authorization was denied or failed";
			throw new OAuthException(400, "OAUTH_AUTHORIZATION_ERROR",
					"Blaze OAuth error: " + error + ". " + desc);
		}
		requireOAuthConfiguration();
		if (!StringUtils.hasText(code)) {
			throw new OAuthException(400, "OAUTH_CALLBACK_INCOMPLETE",
					"OAuth callback incomplete. Return to the dashboard and click Start OAuth again.");
		}
		if (!StringUtils.hasText(state)) {
			throw new OAuthException(400, "OAUTH_CALLBACK_INCOMPLETE",
					"OAuth callback incomplete. Return to the dashboard and click Start OAuth again.");
		}
		OAuthState stored = stateStore.consume(state)
				.orElseThrow(() -> new OAuthException(400, "OAUTH_CALLBACK_INVALID",
						"OAuth authorization expired, already used, or lost due to backend restart. Return to the dashboard and click Start OAuth again."));
		try {
			OAuthTokenResponse response = gateway.exchangeCode(new OAuthTokenExchangeRequest(
					properties.getClientId(),
					properties.getClientSecret(),
					code,
					stored.codeVerifier(),
					properties.getRedirectUri(),
					"authorization_code"));
			return saveCallbackAndSanitize(response);
		} catch (OAuthException e) {
			// Gateway errors (4xx/5xx from Blaze) — let them propagate with specific code
			throw e;
		} catch (ResourceAccessException e) {
			throw new OAuthException(503, "BLAZE_TOKEN_EXCHANGE_UNAVAILABLE",
					"Could not connect to Blaze to exchange the code for a token. Check network/firewall and try again.", e);
		} catch (Exception e) {
			throw new OAuthException(502, "BLAZE_TOKEN_EXCHANGE_ERROR",
					"Unexpected error exchanging code for token: " + e.getMessage(), e);
		}
	}

	public OAuthSessionResponse session() {
		TokenSnapshot token = tokenStore.current().orElse(null);
		OAuthProfileSummary profile = profileService.currentResult().profile();
		return sessionResponse(token, profile);
	}

	public OAuthActionResponse refresh() {
		requireOAuthConfiguration();
		TokenSnapshot current = tokenStore.current()
				.filter(token -> !token.refreshTokenBlank())
				.orElseThrow(() -> new ConfigurationMissingException("Nenhuma credencial de renovacao OAuth esta disponivel"));
		// ponytail: lock local por instância; em multi-instância trocar por Redis/DistLock.
		refreshLock.lock();
		try {
			// Re-check após adquirir o lock: outra thread pode ter renovado já.
			TokenSnapshot latest = tokenStore.current()
					.filter(token -> !token.refreshTokenBlank())
					.orElseThrow(() -> new ConfigurationMissingException("Nenhuma credencial de renovacao OAuth esta disponivel"));
			if (!current.refreshToken().equals(latest.refreshToken())) {
				// já renovado por outra thread — reusa snapshot mais recente sem novo gateway call
				OAuthProfileSyncResult sync = profileService.synchronizeCurrentUser();
				return actionResponse("refreshed", false, false, latest, sync,
						profileMessage(sync, "Sessao Blaze atualizada."));
			}
			return doRefresh(latest);
		} finally {
			refreshLock.unlock();
		}
	}

	private OAuthActionResponse doRefresh(TokenSnapshot current) {
		try {
			OAuthTokenResponse response = gateway.refresh(new OAuthRefreshRequest(
					properties.getClientId(),
					properties.getClientSecret(),
					current.refreshToken()));
			TokenSnapshot snapshot = saveToken(response, current.refreshToken());
			OAuthProfileSyncResult syncResult = profileService.synchronizeCurrentUser();
			return actionResponse("refreshed", true, false, snapshot, syncResult,
					profileMessage(syncResult, "Sessao Blaze atualizada."));
		} catch (OAuthException e) {
			throw e;
		} catch (ResourceAccessException e) {
			throw new OAuthException(503, "BLAZE_TOKEN_REFRESH_UNAVAILABLE",
					"Could not connect to Blaze to refresh the session. Check network/firewall and try again.", e);
		} catch (Exception e) {
			throw new OAuthException(502, "BLAZE_TOKEN_REFRESH_ERROR",
					"Unexpected error refreshing the Blaze session.", e);
		}
	}

	public OAuthActionResponse disconnect() {
		tokenStore.clear();
		profileService.clear();
		stateStore.clear();
		return new OAuthActionResponse(
				"disconnected",
				false,
				true,
				false,
				false,
				false,
				false,
				null,
				null,
				"CONNECT_BLAZE",
				"Conta Blaze desconectada deste backend local.");
	}

	private OAuthCallbackResponse saveCallbackAndSanitize(OAuthTokenResponse response) {
		TokenSnapshot snapshot = saveToken(response, null);
		OAuthProfileSyncResult syncResult = profileService.synchronizeCurrentUser();
		return new OAuthCallbackResponse(
				"stored",
				snapshot.tokenType(),
				snapshot.userId(),
				snapshot.scopes(),
				snapshot.expiresAt(),
				!snapshot.refreshTokenBlank(),
				syncResult.profilePresent(),
				OAuthProfileResponse.from(syncResult.profile()),
				syncResult.status(),
				nextRecommendedAction(snapshot, syncResult.profile()),
				profileMessage(syncResult, "Blaze conectada com sucesso."));
	}

	private TokenSnapshot saveToken(OAuthTokenResponse response, String fallbackRefreshToken) {
		if (!StringUtils.hasText(response.accessToken())) {
			throw new IllegalStateException("Blaze OAuth token response did not include an access token");
		}
		String refreshToken = StringUtils.hasText(response.refreshToken()) ? response.refreshToken() : fallbackRefreshToken;
		Instant expiresAt = response.expiresIn() == null
				? null
				: Instant.now(clock).plusSeconds(Math.max(response.expiresIn(), 0));
		TokenSnapshot snapshot = new TokenSnapshot(
				response.type(),
				response.userId(),
				response.tokenType() == null ? "Bearer" : response.tokenType(),
				response.accessToken(),
				refreshToken,
				expiresAt,
				response.scopes() == null ? List.of() : response.scopes(),
				Instant.now(clock));
		tokenStore.save(snapshot);
		return snapshot;
	}

	private OAuthActionResponse actionResponse(String status, boolean refreshed, boolean disconnected,
			TokenSnapshot token, OAuthProfileSyncResult syncResult, String message) {
		boolean tokenPresent = token != null && !token.accessTokenBlank();
		boolean refreshCredentialPresent = token != null && !token.refreshTokenBlank();
		return new OAuthActionResponse(
				status,
				refreshed,
				disconnected,
				tokenPresent,
				tokenPresent,
				refreshCredentialPresent,
				syncResult.profilePresent(),
				OAuthProfileResponse.from(syncResult.profile()),
				token == null ? null : token.expiresAt(),
				nextRecommendedAction(token, syncResult.profile()),
				message);
	}

	private OAuthSessionResponse sessionResponse(TokenSnapshot token, OAuthProfileSummary profile) {
		boolean tokenPresent = token != null && !token.accessTokenBlank();
		boolean refreshCredentialPresent = token != null && !token.refreshTokenBlank();
		boolean profilePresent = profile != null;
		return new OAuthSessionResponse(
				tokenPresent,
				tokenPresent,
				refreshCredentialPresent,
				profilePresent,
				OAuthProfileResponse.from(profile),
				token == null ? null : token.tokenType(),
				token == null ? null : token.userId(),
				token == null || token.scopes() == null ? List.of() : token.scopes(),
				token == null ? null : token.expiresAt(),
				tokenExpiredOrUnknown(token),
				token == null ? null : token.updatedAt(),
				profile == null ? null : profile.syncedAt(),
				nextRecommendedAction(token, profile));
	}

	private boolean tokenExpiredOrUnknown(TokenSnapshot token) {
		return token == null || token.accessTokenBlank()
				|| token.expiresAt() == null
				|| token.expiresAt().isBefore(Instant.now(clock));
	}

	private String nextRecommendedAction(TokenSnapshot token, OAuthProfileSummary profile) {
		if (token == null || token.accessTokenBlank()) {
			return "CONNECT_BLAZE";
		}
		if (tokenExpiredOrUnknown(token) && !token.refreshTokenBlank()) {
			return "REFRESH_SESSION";
		}
		if (profile == null) {
			return "SYNC_PROFILE_OR_REFRESH_SESSION";
		}
		if (token.refreshTokenBlank()) {
			return "RECONNECT_WITH_OFFLINE_ACCESS";
		}
		return "READY_FOR_EVENTS";
	}

	private String profileMessage(OAuthProfileSyncResult syncResult, String baseMessage) {
		if (!syncResult.attempted()) {
			return baseMessage;
		}
		if (syncResult.profilePresent()) {
			return baseMessage + " Perfil Blaze sincronizado.";
		}
		return baseMessage + " Perfil Blaze indisponivel no momento; a sessao permanece conectada.";
	}

	private void requireOAuthConfiguration() {
		if (!properties.isOAuthConfigured()) {
			throw new ConfigurationMissingException("Blaze OAuth is not configured");
		}
	}
}
