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
	private LocalAuthorizationUrlGenerator urlGenerator;
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
		urlGenerator = new LocalAuthorizationUrlGenerator();
		service = new BlazeOAuthService(properties, gateway, stateStore, tokenStore, urlGenerator, clock);
	}

	@Test
	void startCreatesState() {
		OAuthStartResponse response = service.start();

		assertThat(response.authorizationUrl()).isNotNull();
		assertThat(response.authorizationUrl()).startsWith("https://blaze.stream/oauth2/authorize?");
		assertThat(response.authorizationUrl()).contains("response_type=code");
		assertThat(response.authorizationUrl()).contains("client_id=client-id");
		assertThat(response.authorizationUrl()).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fblaze%2Foauth%2Fcallback");
		assertThat(response.authorizationUrl()).contains("scope=users.read+offline.access");
		assertThat(response.authorizationUrl()).contains("code_challenge_method=S256");
		assertThat(response.authorizationUrl()).contains("code_challenge=");
		assertThat(response.state()).isNotNull();
		assertThat(response.state()).hasSizeGreaterThan(10);
		assertThat(stateStore.size()).isEqualTo(1);

		// The URL should not contain clientSecret or raw tokens
		assertThat(response.authorizationUrl()).doesNotContain("client-secret");
		assertThat(response.authorizationUrl()).doesNotContain("accessToken");
		assertThat(response.authorizationUrl()).doesNotContain("refreshToken");
	}

	@Test
	void callbackRejectsInvalidState() {
		OAuthStartResponse sr = service.start();
		String validCode = "code-1";
		assertThatThrownBy(() -> service.callback(validCode, "wrong-state", null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid or expired OAuth state");
	}

	@Test
	void callbackRejectsMissingCode() {
		OAuthStartResponse startResponse = service.start();

		assertThatThrownBy(() -> service.callback("", startResponse.state(), null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("code is required");
	}

	@Test
	void callbackRejectsMissingState() {
		assertThatThrownBy(() -> service.callback("code-1", null, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("state is required");
	}

	@Test
	void callbackRejectsOAuthError() {
		assertThatThrownBy(() -> service.callback(null, null, "access_denied", "User denied authorization"))
				.isInstanceOf(com.nollen.blaze.common.OAuthException.class)
				.hasMessageContaining("access_denied");
	}

	@Test
	void callbackStoresTokenWithoutReturningRawToken() {
		OAuthStartResponse startResponse = service.start();

		OAuthCallbackResponse response = service.callback("code-1", startResponse.state(), null, null);

		assertThat(response.status()).isEqualTo("stored");
		assertThat(response.refreshTokenPresent()).isTrue();
		assertThat(response.toString()).doesNotContain("access-token-1", "refresh-token-1");
		assertThat(tokenStore.current()).isPresent();
		assertThat(tokenStore.current().orElseThrow().accessToken()).isEqualTo("access-token-1");
	}

	@Test
	void refreshReplacesRefreshToken() {
		OAuthStartResponse startResponse = service.start();
		service.callback("code-1", startResponse.state(), null, null);

		OAuthCallbackResponse response = service.refresh();

		assertThat(response.refreshTokenPresent()).isTrue();
		assertThat(tokenStore.current().orElseThrow().refreshToken()).isEqualTo("refresh-token-2");
	}

	@Test
	void startAuthorizationUrlContainsCodeChallenge() {
		OAuthStartResponse response = service.start();

		// Verify the URL is a valid OAuth authorization URL with PKCE
		String url = response.authorizationUrl();
		assertThat(url).contains("code_challenge=");
		assertThat(url).contains("code_challenge_method=S256");
		assertThat(url).contains("state=" + response.state());

		// The state must be savable and consumable
		assertThat(stateStore.size()).isEqualTo(1);
		assertThat(stateStore.consume(response.state())).isPresent();
	}

	@Test
	void startDoesNotContainClientSecretOrTokens() {
		OAuthStartResponse response = service.start();

		String body = response.toString();
		assertThat(body).doesNotContain("client-secret");
		assertThat(body).doesNotContain("secret");
		assertThat(body).doesNotContain("accessToken");
		assertThat(body).doesNotContain("refreshToken");
		assertThat(body).doesNotContain("codeVerifier");
	}

	@Test
	void callbackWithOAuthErrorFromGatewayReturnsOAuthException() {
		gateway.setThrowOAuthError();
		OAuthStartResponse sr = service.start();

		assertThatThrownBy(() -> service.callback("code-1", sr.state(), null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("Blaze rejected code exchange");
	}

	@Test
	void callbackWithNetworkErrorFromGatewayReturnsOAuthException() {
		gateway.setThrowNetworkError();
		OAuthStartResponse sr = service.start();

		assertThatThrownBy(() -> service.callback("code-1", sr.state(), null, null))
				.isInstanceOf(OAuthException.class)
				.hasMessageContaining("conectar a Blaze");
	}

	@Test
	void callbackResponseNeverContainsSecrets() {
		OAuthStartResponse startResponse = service.start();
		OAuthCallbackResponse response = service.callback("code-1", startResponse.state(), null, null);

		String body = response.toString();
		assertThat(body).doesNotContain("client-secret");
		assertThat(body).doesNotContain("access-token-1");
		assertThat(body).doesNotContain("refresh-token-1");
		assertThat(body).doesNotContain("codeVerifier");
	}

	private static class FakeOAuthGateway implements BlazeOAuthGateway {

		private OAuthGenerateAuthUrlRequest lastGenerateRequest;
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
			return new GeneratedAuthUrl("https://blaze.stream/oauth2/authorize?state=state-1", "state-1", "verifier-1");
		}

		@Override
		public OAuthTokenResponse exchangeCode(OAuthTokenExchangeRequest request) {
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
