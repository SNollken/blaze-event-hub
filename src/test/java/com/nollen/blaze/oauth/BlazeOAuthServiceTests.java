package com.nollen.blaze.oauth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.nollen.blaze.config.BlazeProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
	void startCreatesState() {
		OAuthStartResponse response = service.start();

		assertThat(response.authorizationUrl()).isEqualTo("https://blaze.stream/oauth2/authorize?state=state-1");
		assertThat(response.state()).isEqualTo("state-1");
		assertThat(stateStore.size()).isEqualTo(1);
		assertThat(gateway.lastGenerateRequest.scopes()).containsExactly("users.read", "offline.access");
	}

	@Test
	void callbackRejectsInvalidState() {
		assertThatThrownBy(() -> service.callback("code-1", "wrong-state"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid OAuth state");
	}

	@Test
	void callbackRejectsMissingCode() {
		service.start();

		assertThatThrownBy(() -> service.callback("", "state-1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("code is required");
	}

	@Test
	void callbackStoresTokenWithoutReturningRawToken() {
		service.start();

		OAuthCallbackResponse response = service.callback("code-1", "state-1");

		assertThat(response.status()).isEqualTo("stored");
		assertThat(response.refreshTokenPresent()).isTrue();
		assertThat(response.toString()).doesNotContain("access-token-1", "refresh-token-1");
		assertThat(tokenStore.current()).isPresent();
		assertThat(tokenStore.current().orElseThrow().accessToken()).isEqualTo("access-token-1");
	}

	@Test
	void refreshReplacesRefreshToken() {
		service.start();
		service.callback("code-1", "state-1");

		OAuthCallbackResponse response = service.refresh();

		assertThat(response.refreshTokenPresent()).isTrue();
		assertThat(tokenStore.current().orElseThrow().refreshToken()).isEqualTo("refresh-token-2");
	}

	private static class FakeOAuthGateway implements BlazeOAuthGateway {

		private OAuthGenerateAuthUrlRequest lastGenerateRequest;

		@Override
		public GeneratedAuthUrl generateAuthUrl(OAuthGenerateAuthUrlRequest request) {
			this.lastGenerateRequest = request;
			return new GeneratedAuthUrl("https://blaze.stream/oauth2/authorize?state=state-1", "state-1", "verifier-1");
		}

		@Override
		public OAuthTokenResponse exchangeCode(OAuthTokenExchangeRequest request) {
			return new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-1", "refresh-token-1",
					86400L, List.of("users.read", "offline.access"));
		}

		@Override
		public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
			return new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-2", "refresh-token-2",
					86400L, List.of("users.read", "offline.access"));
		}
	}
}
