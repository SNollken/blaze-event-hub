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
		OAuthStartResponse response = service.start("browser-session-1");

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
		OAuthStartResponse response = service.start("browser-session-1");

		assertThat(response.authorizationUrl()).isNotNull();
		assertThat(response.authorizationUrl()).isNotEmpty();
		assertThat(response.scopes()).containsExactly("users.read", "offline.access");
	}

	@Test
	void startDoesNotContainSecrets() {
		OAuthStartResponse response = service.start("browser-session-1");

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
		service.start("browser-session-1");

		assertThat(stateStore.size()).isEqualTo(1);
		assertThat(stateStore.consume("blaze-state-1")).isPresent();
	}

	@Test
	void callbackRejectsInvalidState() {
		service.start("browser-session-1");
		assertThatThrownBy(() -> service.callback("code-1", "wrong-state", null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("OAuth authorization expired");
	}

	@Test
	void callbackRejectsMissingCode() {
		service.start("browser-session-1");

		assertThatThrownBy(() -> service.callback("", gateway.lastGeneratedState, null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("callback incomplete");
	}

	@Test
	void callbackRejectsMissingState() {
		assertThatThrownBy(() -> service.callback("code-1", null, null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("callback incomplete");
	}

	@Test
	void callbackRejectsOAuthError() {
		assertThatThrownBy(() -> service.callback(null, null, "access_denied", "User denied authorization", "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("access_denied");
	}

	@Test
	void callbackStoresTokenWithoutReturningRawToken() {
		service.start("browser-session-1");

		OAuthCallbackResponse response = service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

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
		service.start("browser-session-1");
		String expectedVerifier = gateway.lastGeneratedVerifier;

		service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

		// The token exchange must use the verifier paired with the returned state.
		assertThat(gateway.lastTokenRequest).isNotNull();
		assertThat(gateway.lastTokenRequest.codeVerifier()).isEqualTo(expectedVerifier);
	}

	@Test
	void refreshReplacesRefreshToken() {
		service.start("browser-session-1");
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

		OAuthActionResponse response = service.refresh();

		assertThat(response.refreshed()).isTrue();
		assertThat(response.refreshCredentialPresent()).isTrue();
		assertThat(tokenStore.current().orElseThrow().refreshToken()).isEqualTo("refresh-token-2");
	}

	@Test
	void concurrentRefreshCallsAreSerialized() throws Exception {
		service.start("browser-session-1");
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

		// Two threads call refresh simultaneously; the lock must serialize them
		// so gateway.refresh is invoked exactly once.
		Thread t1 = new Thread(() -> { try { service.refresh(); } catch (Exception ignored) {} });
		Thread t2 = new Thread(() -> { try { service.refresh(); } catch (Exception ignored) {} });
		t1.start();
		t2.start();
		t1.join(2000);
		t2.join(2000);

		assertThat(gateway.refreshCallCount).isEqualTo(1);
	}

	@Test
	void refreshPreservesRefreshTokenWhenBlazeDoesNotReturnANewOne() {
		service.start("browser-session-1");
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");
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
		service.start("browser-session-1");
		service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

		OAuthSessionResponse session = service.session();

		assertThat(session.connected()).isTrue();
		assertThat(session.profilePresent()).isTrue();
		assertThat(session.profile().displayName()).isEqualTo("Sofia Blaze");
		assertThat(session.nextRecommendedAction()).isEqualTo("READY_FOR_EVENTS");
		assertThat(session.toString()).doesNotContain("access-token-1", "refresh-token-1", "client-secret", "verifier-1");
	}

	@Test
	void callbackProfileFailureKeepsTokenConnected() {
		profileClient.throwError = true;
		service.start("browser-session-1");

		OAuthCallbackResponse response = service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

		assertThat(response.profilePresent()).isFalse();
		assertThat(response.profileSyncStatus()).isEqualTo("unavailable");
		assertThat(tokenStore.current()).isPresent();
		assertThat(service.session().connected()).isTrue();
		assertThat(service.session().nextRecommendedAction()).isEqualTo("SYNC_PROFILE_OR_REFRESH_SESSION");
	}

	@Test
	void disconnectClearsTokenProfileAndPendingStates() {
		service.start("browser-session-1");
		String secondState;
		service.start("browser-session-1");
		secondState = gateway.lastGeneratedState;
		service.callback("auth-code-1", "blaze-state-1", null, null, "browser-session-1");

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
		service.start("browser-session-1");

		assertThatThrownBy(() -> service.callback("code-1", gateway.lastGeneratedState, null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("Blaze rejected code exchange");
	}

	@Test
	void callbackWithNetworkErrorFromGatewayReturnsOAuthException() {
		gateway.setThrowNetworkError();
		service.start("browser-session-1");

		assertThatThrownBy(() -> service.callback("code-1", gateway.lastGeneratedState, null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("connect to Blaze");
	}

	@Test
	void callbackResponseNeverContainsSecrets() {
		service.start("browser-session-1");
		OAuthCallbackResponse response = service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "browser-session-1");

		String body = response.toString();
		assertThat(body).doesNotContain("client-secret");
		assertThat(body).doesNotContain("access-token-1");
		assertThat(body).doesNotContain("refresh-token-1");
		assertThat(body).doesNotContain("codeVerifier");
		assertThat(body).doesNotContain("verifier-1");
	}

	@Test
	void twoStartsKeepBothStatesPending() {
		service.start("browser-session-1");
		String firstState = gateway.lastGeneratedState;
		String firstVerifier = gateway.lastGeneratedVerifier;
		service.start("browser-session-1");
		String secondState = gateway.lastGeneratedState;

		service.callback("auth-code-1", firstState, null, null, "browser-session-1");

		assertThat(gateway.lastTokenRequest.codeVerifier()).isEqualTo(firstVerifier);
		assertThat(stateStore.find(firstState)).isEmpty();
		assertThat(stateStore.find(secondState)).isPresent();
	}

	@Test
	void tokenExchangeFailureConsumesState() {
		service.start("browser-session-1");
		String state = gateway.lastGeneratedState;
		gateway.setThrowOAuthError();

		assertThatThrownBy(() -> service.callback("code-1", state, null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("Blaze rejected code exchange");

		assertThat(stateStore.find(state)).isEmpty();
	}

	@Test
	void callbackFromDifferentBrowserSessionIsRejectedAndStoresNothing() {
		// Account-fixation attack: the victim starts the flow in their own browser
		// session; an attacker who somehow replays the callback from another session
		// must not be able to store their token globally.
		service.start("browser-session-1");

		assertThatThrownBy(() -> service.callback("auth-code-1", gateway.lastGeneratedState, null, null, "attacker-session"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("different browser session");

		assertThat(tokenStore.current()).isEmpty();
		assertThat(profileStore.current()).isEmpty();
		assertThat(gateway.lastTokenRequest).isNull();
	}

	@Test
	void callbackWithoutBrowserSessionIsRejected() {
		service.start("browser-session-1");

		assertThatThrownBy(() -> service.callback("auth-code-1", gateway.lastGeneratedState, null, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("different browser session");

		assertThat(tokenStore.current()).isEmpty();
	}

	@Test
	void rejectedCrossSessionCallbackStillConsumesTheState() {
		// Fail-closed: a rejected attempt must not leave the state reusable. The
		// attacker can only burn their own pending authorization (they never learn
		// foreign state values), so this cannot grief a legitimate flow.
		service.start("browser-session-1");
		String state = gateway.lastGeneratedState;

		assertThatThrownBy(() -> service.callback("auth-code-1", state, null, null, "attacker-session"))
				.isInstanceOf(OAuthException.class);

		assertThat(stateStore.find(state)).isEmpty();
		assertThatThrownBy(() -> service.callback("auth-code-1", state, null, null, "browser-session-1"))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("OAuth authorization expired");
		assertThat(tokenStore.current()).isEmpty();
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
		private int refreshCallCount = 0;

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
			refreshCallCount++;
			// Widen the race window so concurrent callers are blocked on the lock.
			try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
