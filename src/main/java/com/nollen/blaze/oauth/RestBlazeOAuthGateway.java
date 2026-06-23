package com.nollen.blaze.oauth;

import com.nollen.blaze.config.BlazeProperties;

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
		OAuthTokenResponse response = restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/token")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(OAuthTokenResponse.class);
		if (response == null) {
			throw new IllegalStateException("Empty Blaze OAuth token response");
		}
		return response;
	}

	@Override
	public OAuthTokenResponse refresh(OAuthRefreshRequest request) {
		OAuthTokenResponse response = restClient.post()
				.uri(properties.getAuthBaseUrl() + "/bapi/oauth2/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(OAuthTokenResponse.class);
		if (response == null) {
			throw new IllegalStateException("Empty Blaze OAuth refresh response");
		}
		return response;
	}

	private record GenerateAuthUrlResponse(String url, String state, String codeVerifier) {
	}
}
