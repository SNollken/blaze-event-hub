package com.blaze.eventhub.oauth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.common.OAuthException;
import com.blaze.eventhub.config.BlazeProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlazeOAuthServiceTests {

	private BlazeOAuthService service;
	private InMemoryOAuthStateStore stateStore;
	private InMemoryTokenStore tokenStore;
	private InMemoryOAuthProfileStore profileStore;
	private FakeOAuthGateway gateway;
	private FakeOAuthProfileClient profileClient;
	private Clock clock;

	@BeforeEach
	void setUp() {
		BlazeProperties properties = new BlazeProperties();
		properties.setClientId("client-id");
		properties.setClientSecret("client-secret");
		properties.setRedirectUri("http://localhost:8080/api/blaze/oauth/callback");
		properties.setScopes(List.of("users.read", "offline.access"));
		clock = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);
		stateStore = new InMemoryOAuthStateStore(clock);
		tokenStore = new InMemoryTokenStore();
		profileStore = new InMemoryOAuthProfileStore();
		gateway = new FakeOAuthGateway();
		profileClient = new FakeOAuthProfileClient();
		OAuthProfileService profileService = new OAuthProfileService(profileClient, profileStore, clock);
		service = new BlazeOAuthService(properties, gateway, stateStore, tokenStore, profileService, clock);
	}

	@Test
	void startCallsGenerateAuthUrl() {
		OAuthStartResponse response = service.start();

		assertThat(gateway.lastGenerateRequest).isNotNull();
		assertThat(gateway.lastGenerateRequest.clientId()).isEqualTo("client-id");
		assertThat(gateway.lastGenerateRequest.clientSecret()).isEqualTo("client-secret");
		assertThat(gateway.lastGenerateRequest.redirectUri()).isEqualTo("http://localhost:8080/api/blaze/oauth/callback");
		assertThat(gateway.lastGenerateRequest.scopes()).containsExactly("users.read", "offline.access");
		assertThat(response.authorizationUrl()).startsWith("https://blaze.stream/oauth2/authorize");
		assertThat(stateStore.find("blaze-state-1")).isPresent();
		assertThat(stateStore.size()).isEqualTo(1);
	}

	@Test
	void startReturnsAuthorizationUrl() {
		OAuthStartResponse response = service.start();

		assertThat(response.authorizationUrl()).isNotNull();
		assertThat(response.authorizationUrl()).isNotEmpty();
		assertThat(response.scopes()).containsExactly("users.read", "offline.access");
	}

	@Test
	void startDoesNotContainSecrets() {
		OAuthStartResponse response = service.start();

		String body = response.toString();
		assertThat(body).doesNotContain("client-secret");
		assertThat(body).doesNotContain("secret");
		assertThat(body).doesNotContain("accessToken");
		assertThat(body).doesNotContain("refreshToken");
		assertThat(body).doesNotContain("codeVerifier");
		assertThat(body).doesNotContain("verifier-1");
	}

	@Test
	void startPersistsState() {
		service.start();

		assertThat(stateStore.size()).isEqualTo(1);
		assertThat(stateStore.consume("blaze-state-1")).isPresent();
	}

	@Test
	void callbackRejectsInvalidState() {
		service.start();
		assertThatThrownBy(() -> service.callback("code-1", "wrong-state", null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("Autorizacao OAuth expirada");
	}

	@Test
	void callbackRejectsMissingCode() {
		service.start();

		assertThatThrownBy(() -> service.callback("", gateway.lastGeneratedState, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("callback incompleto");
	}

	@Test
	void callbackRejectsMissingState() {
		assertThatThrownBy(() -> service.callback("code-1", null, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("callback incompleto");
	}

	@Test
	void callbackRejectsOAuthError() {
		assertThatThrownBy(() -> service.callback(null, null, "access_denied", "User denied authorization"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("access_denied");
	}

	@Test
	void callbackStoresTokenWithoutReturningRawToken() {
		service.start();

		OAuthCallbackResponse response = service.callback("auth-code-1", gateway.lastGeneratedState, null, null);

		assertThat(response.status()).isEqualTo("stored");
		assertThat(response.refreshTokenPresent()).isTrue();
		assertThat(response.profilePresent()).isTrue();
		assertThat(response.profile().displayName()).isEqualTo("Sofia Blaze");
		assertThat(response.toString()).doesNotContain("access-token-1", "refresh-token-1");
		assertThat(tokenStore.current()).isPresent();
		assertThat(tokenStore.current().orElseThrow().accessToken()).isEqualTo("access-token-1");
		assertThat(profileStore.current()).isPresent();
	}

	@Test
	void callbackUsesCodeVerifierFromBlaze() {
		service.start();
		String expectedVerifier = gateway.lastGeneratedVerifier;

		service.callback("auth-code-1", gateway.lastGeneratedState, null, null);

		// The token exchange must use the verifier paired with the returned state.
		assertThat(gateway.lastTokenRequest).isNotNull();
		assertThat(gateway.lastTokenRequest.codeVerifier()).isEqualTo(expectedVerifier);
	}

	@Test
	void refreshReplacesRefreshToken() {
		service.start();
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null);

		OAuthActionResponse response = service.refresh();

		assertThat(response.refreshed()).isTrue();
		assertThat(response.refreshCredentialPresent()).isTrue();
		assertThat(tokenStore.current().orElseThrow().refreshToken()).isEqualTo("refresh-token-2");
	}

	@Test
	void refreshPreservesRefreshTokenWhenBlazeDoesNotReturnANewOne() {
		service.start();
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null);
		gateway.refreshTokenOnRefresh = null;

		OAuthActionResponse response = service.refresh();

		assertThat(response.refreshed()).isTrue();
		assertThat(response.refreshCredentialPresent()).isTrue();
		assertThat(tokenStore.current().orElseThrow().refreshToken()).isEqualTo("refresh-token-1");
	}

	@Test
	void sessionWithoutTokenIsDisconnected() {
		OAuthSessionResponse session = service.session();

		assertThat(session.connected()).isFalse();
		assertThat(session.tokenPresent()).isFalse();
		assertThat(session.refreshCredentialPresent()).isFalse();
		assertThat(session.profilePresent()).isFalse();
		assertThat(session.nextRecommendedAction()).isEqualTo("CONNECT_BLAZE");
	}

	@Test
	void sessionWithTokenAndProfileIsReadyForEvents() {
		service.start();
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null);

		OAuthSessionResponse session = service.session();

		assertThat(session.connected()).isTrue();
		assertThat(session.profilePresent()).isTrue();
		assertThat(session.profile().rawAvailable()).isFalse();
		assertThat(session.profile().displayName()).isEqualTo("Sofia Blaze");
		assertThat(session.nextRecommendedAction()).isEqualTo("READY_FOR_EVENTS");
		assertThat(session.toString()).doesNotContain("access-token-1", "refresh-token-1", "client-secret", "verifier-1");
	}

	@Test
	void callbackProfileFailureKeepsTokenConnected() {
		profileClient.throwError = true;
		service.start();

		OAuthCallbackResponse response = service.callback("auth-code-1", gateway.lastGeneratedState, null, null);

		assertThat(response.profilePresent()).isFalse();
		assertThat(response.profileSyncStatus()).isEqualTo("unavailable");
		assertThat(tokenStore.current()).isPresent();
		assertThat(service.session().connected()).isTrue();
		assertThat(service.session().nextRecommendedAction()).isEqualTo("SYNC_PROFILE_OR_REFRESH_SESSION");
	}

	@Test
	void disconnectClearsTokenProfileAndPendingStates() {
		service.start();
		String secondState;
		service.start();
		secondState = gateway.lastGeneratedState;
		service.callback("auth-code-1", "blaze-state-1", null, null);

		OAuthActionResponse response = service.disconnect();

		assertThat(response.disconnected()).isTrue();
		assertThat(tokenStore.current()).isEmpty();
		assertThat(profileStore.current()).isEmpty();
		assertThat(stateStore.find(secondState)).isEmpty();
		assertThat(service.session().connected()).isFalse();
	}

	@Test
	void callbackWithOAuthErrorFromGatewayReturnsOAuthException() {
		gateway.setThrowOAuthError();
		service.start();

		assertThatThrownBy(() -> service.callback("code-1", gateway.lastGeneratedState, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("Blaze rejected code exchange");
	}

	@Test
	void callbackWithNetworkErrorFromGatewayReturnsOAuthException() {
		gateway.setThrowNetworkError();
		service.start();

		assertThatThrownBy(() -> service.callback("code-1", gateway.lastGeneratedState, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("conectar a Blaze");
	}

	@Test
	void callbackResponseNeverContainsSecrets() {
		service.start();
		OAuthCallbackResponse response = service.callback("auth-code-1", gateway.lastGeneratedState, null, null);

		String body = response.toString();
		assertThat(body).doesNotContain("client-secret");
		assertThat(body).doesNotContain("access-token-1");
		assertThat(body).doesNotContain("refresh-token-1");
		assertThat(body).doesNotContain("codeVerifier");
		assertThat(body).doesNotContain("verifier-1");
	}

	@Test
	void twoStartsKeepBothStatesPending() {
		service.start();
		String firstState = gateway.lastGeneratedState;
		String firstVerifier = gateway.lastGeneratedVerifier;
		service.start();
		String secondState = gateway.lastGeneratedState;

		service.callback("auth-code-1", firstState, null, null);

		assertThat(gateway.lastTokenRequest.codeVerifier()).isEqualTo(firstVerifier);
		assertThat(stateStore.find(firstState)).isEmpty();
		assertThat(stateStore.find(secondState)).isPresent();
	}

	@Test
	void tokenExchangeFailureDoesNotDiscardValidState() {
		service.start();
		String state = gateway.lastGeneratedState;
		gateway.setThrowOAuthError();

		assertThatThrownBy(() -> service.callback("code-1", state, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("Blaze rejected code exchange");

		assertThat(stateStore.find(state)).isPresent();
	}

	private static class FakeOAuthGateway implements BlazeOAuthGateway {

		private OAuthGenerateAuthUrlRequest lastGenerateRequest;
		private OAuthTokenExchangeRequest lastTokenRequest;
		private String lastGeneratedState;
		private String lastGeneratedVerifier;
		private int generatedCount = 0;
		private boolean throwOAuthError = false;
		private boolean throwNetworkError = false;
		private String refreshTokenOnRefresh = "refresh-token-2";

		void setThrowOAuthError() {
			this.throwOAuthError = true;
		}

		void setThrowNetworkError() {
			this.throwNetworkError = true;
		}

		@Override
		public GeneratedAuthUrl generateAuthUrl(OAuthGenerateAuthUrlRequest request) {
			this.lastGenerateRequest = request;
			generatedCount++;
			lastGeneratedState = "blaze-state-" + generatedCount;
			lastGeneratedVerifier = "verifier-" + generatedCount;
			return new GeneratedAuthUrl(
					"https://blaze.stream/oauth2/authorize?response_type=code&client_id=client-id&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fblaze%2Foauth%2Fcallback&scope=users.read+offline.access&state=" + lastGeneratedState + "&code_challenge_method=S256&code_challenge=blaze-challenge",
					lastGeneratedState,
					lastGeneratedVerifier);
		}

		@Override
		public OAuthTokenResponse exchangeCode(OAuthTokenExchangeRequest request) {
			this.lastTokenRequest = request;
			if (throwOAuthError) {
				throw new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED", "Blaze rejected code exchange");
			}
			if (throwNetworkError) {
				throw new ResourceAccessException("I/O error on POST request: connection refused");
			}
			return new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-1", "refresh-token-1",
					86400L, List.of("users.read", "offline.access"));
		}

		@Override
		public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
			if (throwOAuthError) {
				throw new OAuthException(400, "BLAZE_TOKEN_REFRESH_REJECTED", "Blaze rejected refresh");
			}
			if (throwNetworkError) {
				throw new ResourceAccessException("I/O error on POST request: connection refused");
			}
			return new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-2", refreshTokenOnRefresh,
					86400L, List.of("users.read", "offline.access"));
		}
	}

	private static class FakeOAuthProfileClient implements OAuthProfileClient {

		private boolean throwError = false;

		@Override
		public Map<String, Object> getCurrentUserProfile() {
			if (throwError) {
				throw new IllegalStateException("profile unavailable");
			}
			return Map.of(
					"id", "user-1",
					"username", "sofia",
					"displayName", "Sofia Blaze",
					"avatarUrl", "https://cdn.example.test/avatar.png",
					"accessToken", "must-not-be-used");
		}
	}
}
