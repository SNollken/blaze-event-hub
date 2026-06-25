package com.nollen.blaze.oauth;

import com.nollen.blaze.common.OAuthException;
import com.nollen.blaze.config.BlazeProperties;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class RestBlazeOAuthGateway implements BlazeOAuthGateway {

	private final BlazeProperties properties;
	private final RestClient restClient;

	public RestBlazeOAuthGateway(BlazeProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.restClient = restClientBuilder.build();
	}

	@Override
	public GeneratedAuthUrl generateAuthUrl(OAuthGenerateAuthUrlRequest request) {
		GenerateAuthUrlResponse response = restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/generate-auth-url")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(GenerateAuthUrlResponse.class);
		if (response == null) {
			throw new IllegalStateException("Empty Blaze OAuth generate-auth-url response");
		}
		return new GeneratedAuthUrl(response.url(), response.state(), response.codeVerifier());
	}

	@Override
	public OAuthTokenResponse exchangeCode(OAuthTokenExchangeRequest request) {
		// OAuth2 token exchange must use application/x-www-form-urlencoded
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", request.grantType());
		form.add("code", request.code());
		form.add("redirect_uri", request.redirectUri());
		form.add("client_id", request.clientId());
		form.add("client_secret", request.clientSecret());
		form.add("code_verifier", request.codeVerifier());

		return restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String body = new String(res.getBody().readAllBytes());
					throw new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED",
							"Blaze rejeitou a troca do codigo por token: " + truncate(body, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String body = new String(res.getBody().readAllBytes());
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"Blaze retornou erro interno durante troca de token: " + truncate(body, 200));
				})
				.body(OAuthTokenResponse.class);
	}

	@Override
	public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
		// OAuth2 refresh must use application/x-www-form-urlencoded
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "refresh_token");
		form.add("refresh_token", request.refreshToken());
		form.add("client_id", request.clientId());
		form.add("client_secret", request.clientSecret());

		return restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/refresh")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String body = new String(res.getBody().readAllBytes());
					throw new OAuthException(400, "BLAZE_TOKEN_REFRESH_REJECTED",
							"Blaze rejeitou a renovacao do token: " + truncate(body, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String body = new String(res.getBody().readAllBytes());
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"Blaze retornou erro interno durante renovacao de token: " + truncate(body, 200));
				})
				.body(OAuthTokenResponse.class);
	}

	private static String truncate(String value, int max) {
		if (value == null || value.length() <= max) return value == null ? "" : value;
		return value.substring(0, max) + "...";
	}

	private record GenerateAuthUrlResponse(String url, String state, String codeVerifier) {
	}
}
