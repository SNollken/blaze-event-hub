package com.nollen.blaze.oauth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.nollen.blaze.common.OAuthException;
import com.nollen.blaze.config.BlazeProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlazeOAuthServiceTests {

	private BlazeOAuthService service;
	private InMemoryOAuthStateStore stateStore;
	private InMemoryTokenStore tokenStore;
	private FakeOAuthGateway gateway;
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
		gateway = new FakeOAuthGateway();
		service = new BlazeOAuthService(properties, gateway, stateStore, tokenStore, clock);
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
				.hasMessageContaining("OAuth state expirou");
	}

	@Test
	void callbackRejectsMissingCode() {
		service.start();

		assertThatThrownBy(() -> service.callback("", gateway.lastGeneratedState, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("sem codigo");
	}

	@Test
	void callbackRejectsMissingState() {
		assertThatThrownBy(() -> service.callback("code-1", null, null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("sem state");
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
		assertThat(response.toString()).doesNotContain("access-token-1", "refresh-token-1");
		assertThat(tokenStore.current()).isPresent();
		assertThat(tokenStore.current().orElseThrow().accessToken()).isEqualTo("access-token-1");
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

		OAuthCallbackResponse response = service.refresh();

		assertThat(response.refreshTokenPresent()).isTrue();
		assertThat(tokenStore.current().orElseThrow().refreshToken()).isEqualTo("refresh-token-2");
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
			return new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-2", "refresh-token-2",
					86400L, List.of("users.read", "offline.access"));
		}
	}
}
