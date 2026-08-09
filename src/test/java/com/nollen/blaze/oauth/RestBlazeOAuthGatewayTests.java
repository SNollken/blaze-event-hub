package com.nollen.blaze.oauth;

import java.io.IOException;
import java.util.List;

import com.nollen.blaze.common.OAuthException;
import com.nollen.blaze.config.BlazeProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

/**
 * Network-level failures (connect timeout, refused, DNS) must surface as a
 * controlled OAuthException(502, BLAZE_UNREACHABLE) — before this mapping they
 * fell through to the generic 500 handler and the OAuth page showed
 * "Unexpected server error" with no hint that Blaze itself was down.
 */
class RestBlazeOAuthGatewayTests {

	private MockRestServiceServer server;
	private RestBlazeOAuthGateway gateway;

	@BeforeEach
	void setUp() {
		BlazeProperties properties = new BlazeProperties();
		properties.setAuthBaseUrl("https://blaze.stream");
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new RestBlazeOAuthGateway(properties, builder);
	}

	@Test
	void networkFailureOnGenerateAuthUrlMapsToBlazeUnreachable() {
		server.expect(once(), requestTo("https://blaze.stream/bapi/oauth2/generate-auth-url"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withException(new IOException("Connection refused")));

		assertThatThrownBy(() -> gateway.generateAuthUrl(
				new OAuthGenerateAuthUrlRequest("client", "secret", "http://localhost/callback", List.of("users.read"))))
				.isInstanceOf(OAuthException.class)
				.satisfies(ex -> {
					OAuthException oauthEx = (OAuthException) ex;
					assertThat(oauthEx.getHttpStatus()).isEqualTo(502);
					assertThat(oauthEx.getErrorCode()).isEqualTo("BLAZE_UNREACHABLE");
					assertThat(oauthEx.getMessage()).contains("generate the authorization URL");
					assertThat(oauthEx.getCause()).isNotNull();
				});
	}

	@Test
	void networkFailureOnExchangeCodeMapsToBlazeUnreachable() {
		server.expect(once(), requestTo("https://blaze.stream/bapi/oauth2/token"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withException(new IOException("Connection timed out")));

		assertThatThrownBy(() -> gateway.exchangeCode(new OAuthTokenExchangeRequest(
				"client", "secret", "auth-code", "verifier", "http://localhost/callback", "authorization_code")))
				.isInstanceOf(OAuthException.class)
				.satisfies(ex -> {
					OAuthException oauthEx = (OAuthException) ex;
					assertThat(oauthEx.getHttpStatus()).isEqualTo(502);
					assertThat(oauthEx.getErrorCode()).isEqualTo("BLAZE_UNREACHABLE");
					assertThat(oauthEx.getMessage()).contains("exchange the authorization code");
				});
	}

	@Test
	void networkFailureOnRefreshMapsToBlazeUnreachable() {
		server.expect(once(), requestTo("https://blaze.stream/bapi/oauth2/refresh"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withException(new IOException("No route to host")));

		assertThatThrownBy(() -> gateway.refresh(new OAuthRefreshRequest("client", "secret", "refresh-token")))
				.isInstanceOf(OAuthException.class)
				.satisfies(ex -> {
					OAuthException oauthEx = (OAuthException) ex;
					assertThat(oauthEx.getHttpStatus()).isEqualTo(502);
					assertThat(oauthEx.getErrorCode()).isEqualTo("BLAZE_UNREACHABLE");
					assertThat(oauthEx.getMessage()).contains("refresh the token");
				});
	}
}
