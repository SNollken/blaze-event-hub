package com.blaze.eventhub.oauth;

import com.blaze.eventhub.common.OAuthException;
import com.blaze.eventhub.config.BlazeProperties;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
				.accept(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String body = new String(res.getBody().readAllBytes());
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_AUTH_URL_REJECTED",
							"Blaze rejeitou a geracao da URL de autorizacao: " + truncate(body, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String body = new String(res.getBody().readAllBytes());
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_AUTH_URL_ERROR",
							"Blaze retornou erro interno ao gerar URL: " + truncate(body, 200));
				})
				.body(GenerateAuthUrlResponse.class);
		if (response == null) {
			throw new IllegalStateException("Empty Blaze OAuth generate-auth-url response");
		}
		return new GeneratedAuthUrl(response.url(), response.state(), response.codeVerifier());
	}

	@Override
	public OAuthTokenResponse exchangeCode(OAuthTokenExchangeRequest request) {
		// OAuth2 token exchange — JSON camelCase conforme docs Blaze
		TokenRequestBody body = new TokenRequestBody(
				request.clientId(),
				request.clientSecret(),
				request.code(),
				request.codeVerifier(),
				request.redirectUri(),
				request.grantType());

		return restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/token")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String responseBody = new String(res.getBody().readAllBytes());
					int status = res.getStatusCode().value();
					if (status == 401) {
						throw new OAuthException(401, "BLAZE_TOKEN_EXCHANGE_REJECTED",
								"Blaze rejeitou as credenciais. Verifique Client Secret e Redirect URI.");
					}
					throw new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED",
							"Blaze rejeitou a troca do codigo por token: " + truncate(responseBody, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String responseBody = new String(res.getBody().readAllBytes());
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"Blaze retornou erro interno durante troca de token: " + truncate(responseBody, 200));
				})
				.body(OAuthTokenResponse.class);
	}

	@Override
	public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
		// OAuth2 refresh — JSON camelCase conforme docs Blaze
		RefreshRequestBody body = new RefreshRequestBody(
				request.clientId(),
				request.clientSecret(),
				request.refreshToken());

		return restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String responseBody = new String(res.getBody().readAllBytes());
					throw new OAuthException(400, "BLAZE_TOKEN_REFRESH_REJECTED",
							"Blaze rejeitou a renovacao do token: " + truncate(responseBody, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String responseBody = new String(res.getBody().readAllBytes());
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"Blaze retornou erro interno durante renovacao de token: " + truncate(responseBody, 200));
				})
				.body(OAuthTokenResponse.class);
	}

	private static String truncate(String value, int max) {
		if (value == null || value.length() <= max) return value == null ? "" : value;
		return value.substring(0, max) + "...";
	}

	private record GenerateAuthUrlResponse(String url, String state, String codeVerifier) {
	}

	// JSON camelCase bodies conforme documentacao oficial Blaze
	private record TokenRequestBody(
			String clientId,
			String clientSecret,
			String code,
			String codeVerifier,
			String redirectUri,
			String grantType) {
	}

	private record RefreshRequestBody(
			String clientId,
			String clientSecret,
			String refreshToken) {
	}
}
