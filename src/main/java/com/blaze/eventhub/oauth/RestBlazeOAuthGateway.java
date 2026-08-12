package com.blaze.eventhub.oauth;

import com.blaze.eventhub.common.OAuthException;
import com.blaze.eventhub.config.BlazeProperties;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
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
		GenerateAuthUrlResponse response;
		try {
			response = restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/generate-auth-url")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String body = res.getBody() != null ? new String(res.getBody().readAllBytes()) : "";
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_AUTH_URL_REJECTED",
							"Blaze rejected authorization URL generation: " + truncate(body, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String body = res.getBody() != null ? new String(res.getBody().readAllBytes()) : "";
					throw new OAuthException(res.getStatusCode().value(), "BLAZE_AUTH_URL_ERROR",
							"Blaze returned an internal error generating the URL: " + truncate(body, 200));
				})
				.body(GenerateAuthUrlResponse.class);
		}
		catch (ResourceAccessException ex) {
			throw unreachable("generate the authorization URL", ex);
		}
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

		try {
			return restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/token")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String responseBody = res.getBody() != null ? new String(res.getBody().readAllBytes()) : "";
					int status = res.getStatusCode().value();
					if (status == 401) {
						throw new OAuthException(401, "BLAZE_TOKEN_EXCHANGE_REJECTED",
								"Blaze rejected the credentials. Verify the Client Secret and Redirect URI.");
					}
					throw new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED",
							"Blaze rejected the code exchange for a token: " + truncate(responseBody, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String responseBody = res.getBody() != null ? new String(res.getBody().readAllBytes()) : "";
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"Blaze returned an internal error during token exchange: " + truncate(responseBody, 200));
				})
				.body(OAuthTokenResponse.class);
		}
		catch (ResourceAccessException ex) {
			throw unreachable("exchange the authorization code", ex);
		}
	}

	@Override
	public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
		// OAuth2 refresh — JSON camelCase conforme docs Blaze
		RefreshRequestBody body = new RefreshRequestBody(
				request.clientId(),
				request.clientSecret(),
				request.refreshToken());

		try {
			return restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					String responseBody = res.getBody() != null ? new String(res.getBody().readAllBytes()) : "";
					throw new OAuthException(400, "BLAZE_TOKEN_REFRESH_REJECTED",
							"Blaze rejected the token refresh: " + truncate(responseBody, 200));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					String responseBody = res.getBody() != null ? new String(res.getBody().readAllBytes()) : "";
					throw new OAuthException(502, "BLAZE_TOKEN_SERVER_ERROR",
							"Blaze returned an internal error during token refresh: " + truncate(responseBody, 200));
				})
				.body(OAuthTokenResponse.class);
		}
		catch (ResourceAccessException ex) {
			throw unreachable("refresh the token", ex);
		}
	}

	/**
	 * Network-level failure (connect timeout, refused, DNS). Without this mapping
	 * the exception falls through to the generic 500 handler and the OAuth page
	 * shows "Unexpected server error" with no hint that Blaze itself is down.
	 */
	private static OAuthException unreachable(String action, ResourceAccessException ex) {
		return new OAuthException(502, "BLAZE_UNREACHABLE",
				"Could not reach Blaze to " + action + ". The Blaze service may be down or unreachable. Try again in a few minutes.",
				ex);
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
