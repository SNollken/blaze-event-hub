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
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_AUTH_URL_REJECTED",
							"A Blaze recusou a geracao da URL de autorizacao.");
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_AUTH_URL_ERROR",
							"A Blaze ficou indisponivel ao gerar a URL de autorizacao.");
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
					int status = res.getStatusCode().value();
					if (status == 401) {
						throw new OAuthException(401, "BLAZE_TOKEN_EXCHANGE_REJECTED",
								"Blaze rejeitou as credenciais. Verifique Client Secret e Redirect URI.");
					}
					throw new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED",
							"A Blaze recusou a troca do codigo por token.");
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"A Blaze ficou indisponivel durante a troca de token.");
				})
				.body(OAuthTokenResponse.class);
	}

	@Override
	public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
		// OAuth2 refresh — JSON camelCase conforme docs Blaze
		RefreshRequestBody body = new RefreshRequestBody(
				request.clientId(),
				request.clientSecret(),
				request.refreshToken(),
				"refresh_token");
		try {
			return executeRefresh("/bapi/oauth2/token", body);
		}
		catch (OAuthException primaryFailure) {
			if (!supportsLegacyRefreshFallback(primaryFailure.getHttpStatus())) {
				throw primaryFailure;
			}
			LegacyRefreshRequestBody legacyBody = new LegacyRefreshRequestBody(
					request.clientId(),
					request.clientSecret(),
					request.refreshToken());
			return executeRefresh("/bapi/oauth2/refresh", legacyBody);
		}
	}

	private OAuthTokenResponse executeRefresh(String path, Object body) {
		return restClient.post()
				.uri(properties.getAuthBaseUrl() + path)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_TOKEN_REFRESH_REJECTED",
							"A Blaze recusou a renovacao da sessao.");
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_TOKEN_SERVER_ERROR",
							"A Blaze ficou indisponivel durante a renovacao da sessao.");
				})
				.body(OAuthTokenResponse.class);
	}

	private static boolean supportsLegacyRefreshFallback(int status) {
		return status == 400 || status == 404 || status == 405;
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
			String refreshToken,
			String grantType) {
	}

	private record LegacyRefreshRequestBody(
			String clientId,
			String clientSecret,
			String refreshToken) {
	}
}
