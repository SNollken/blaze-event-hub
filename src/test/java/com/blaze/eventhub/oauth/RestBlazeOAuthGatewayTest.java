package com.blaze.eventhub.oauth;

import java.util.List;

import com.blaze.eventhub.common.OAuthException;
import com.blaze.eventhub.config.BlazeProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestBlazeOAuthGatewayTest {

	@Test
	void generateAuthUrlDoesNotExposeUpstreamBody() {
		BlazeProperties properties = properties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestBlazeOAuthGateway gateway = new RestBlazeOAuthGateway(properties, builder);

		server.expect(requestTo("https://blaze.example/bapi/oauth2/generate-auth-url"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.body("{\"message\":\"upstream-sensitive-detail\"}"));

		assertThatThrownBy(() -> gateway.generateAuthUrl(new OAuthGenerateAuthUrlRequest(
				"client-id", "client-secret", "https://app.example/callback", List.of("users.read"))))
				.isInstanceOfSatisfying(OAuthException.class, error -> {
					assertThat(error.getErrorCode()).isEqualTo("BLAZE_AUTH_URL_REJECTED");
					assertThat(error.getHttpStatus()).isEqualTo(400);
					assertThat(error.getMessage()).doesNotContain("upstream-sensitive-detail");
				});
		server.verify();
	}

	@Test
	void exchangeCodeDoesNotExposeUpstreamBody() {
		BlazeProperties properties = properties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestBlazeOAuthGateway gateway = new RestBlazeOAuthGateway(properties, builder);

		server.expect(requestTo("https://blaze.example/bapi/oauth2/token"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.body("{\"message\":\"upstream-sensitive-detail\"}"));

		assertThatThrownBy(() -> gateway.exchangeCode(new OAuthTokenExchangeRequest(
				"client-id", "client-secret", "code", "verifier", "https://app.example/callback",
				"authorization_code")))
				.isInstanceOfSatisfying(OAuthException.class, error -> {
					assertThat(error.getErrorCode()).isEqualTo("BLAZE_TOKEN_EXCHANGE_REJECTED");
					assertThat(error.getHttpStatus()).isEqualTo(400);
					assertThat(error.getMessage()).doesNotContain("upstream-sensitive-detail");
				});
		server.verify();
	}

	@Test
	void refreshUsesTokenEndpointAndRefreshGrant() {
		BlazeProperties properties = new BlazeProperties();
		properties.setAuthBaseUrl("https://blaze.example");
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestBlazeOAuthGateway gateway = new RestBlazeOAuthGateway(properties, builder);

		server.expect(requestTo("https://blaze.example/bapi/oauth2/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "clientId": "client-id",
						  "clientSecret": "client-secret",
						  "refreshToken": "refresh-token",
						  "grantType": "refresh_token"
						}
						"""))
				.andRespond(withSuccess("""
						{
						  "accessToken": "new-access-token",
						  "refreshToken": "new-refresh-token",
						  "tokenType": "Bearer",
						  "expiresIn": 3600
						}
						""", MediaType.APPLICATION_JSON));

		OAuthTokenResponse response = gateway.refresh(
				new OAuthRefreshRequest("client-id", "client-secret", "refresh-token"));

		assertThat(response.accessToken()).isEqualTo("new-access-token");
		assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 404, 405 })
	void refreshFallsBackToLegacyEndpointOnlyForSupportedCompatibilityStatuses(int primaryStatus) {
		BlazeProperties properties = properties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestBlazeOAuthGateway gateway = new RestBlazeOAuthGateway(properties, builder);

		server.expect(requestTo("https://blaze.example/bapi/oauth2/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{
						  "clientId": "client-id",
						  "clientSecret": "client-secret",
						  "refreshToken": "refresh-token",
						  "grantType": "refresh_token"
						}
						"""))
				.andRespond(withStatus(HttpStatus.valueOf(primaryStatus)).body("{\"message\":\"unsupported\"}"));
		server.expect(requestTo("https://blaze.example/bapi/oauth2/refresh"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{
						  "clientId": "client-id",
						  "clientSecret": "client-secret",
						  "refreshToken": "refresh-token"
						}
						""", true))
				.andRespond(withSuccess("""
						{
						  "accessToken": "legacy-access-token",
						  "refreshToken": "legacy-refresh-token",
						  "tokenType": "Bearer",
						  "expiresIn": 3600
						}
						""", MediaType.APPLICATION_JSON));

		OAuthTokenResponse response = gateway.refresh(refreshRequest());

		assertThat(response.accessToken()).isEqualTo("legacy-access-token");
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 403, 409, 429, 500, 503 })
	void refreshDoesNotFallbackForOtherErrorsAndPreservesStatus(int status) {
		BlazeProperties properties = properties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestBlazeOAuthGateway gateway = new RestBlazeOAuthGateway(properties, builder);

		server.expect(requestTo("https://blaze.example/bapi/oauth2/token"))
				.andRespond(withStatus(HttpStatus.valueOf(status))
						.body("{\"message\":\"upstream-sensitive-detail\"}"));

		assertThatThrownBy(() -> gateway.refresh(refreshRequest()))
				.isInstanceOfSatisfying(OAuthException.class, error -> {
					assertThat(error.getHttpStatus()).isEqualTo(status);
					assertThat(error.getMessage()).doesNotContain("upstream-sensitive-detail");
				});
		server.verify();
	}

	private static BlazeProperties properties() {
		BlazeProperties properties = new BlazeProperties();
		properties.setAuthBaseUrl("https://blaze.example");
		return properties;
	}

	private static OAuthRefreshRequest refreshRequest() {
		return new OAuthRefreshRequest("client-id", "client-secret", "refresh-token");
	}
}
