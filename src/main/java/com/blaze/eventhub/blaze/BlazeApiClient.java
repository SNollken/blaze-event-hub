package com.blaze.eventhub.blaze;

import java.util.Map;

import com.blaze.eventhub.common.ConfigurationMissingException;
import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.oauth.TokenStore;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BlazeApiClient {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
	};

	private final BlazeProperties properties;
	private final TokenStore tokenStore;
	private final BlazeApiHeaders apiHeaders;
	private final RestClient restClient;

	public BlazeApiClient(BlazeProperties properties, TokenStore tokenStore, BlazeApiHeaders apiHeaders,
			RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.tokenStore = tokenStore;
		this.apiHeaders = apiHeaders;
		this.restClient = restClientBuilder.baseUrl(properties.getApiBaseUrl()).build();
	}

	public Map<String, Object> getCurrentUserProfile() {
		TokenSnapshot token = requireToken();
		return get("/v1/users/profile", token);
	}

	public Map<String, Object> getChannelsBySlug(String slug) {
		TokenSnapshot token = requireToken();
		if (slug == null || slug.isBlank()) {
			throw new IllegalArgumentException("slug is required");
		}
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/v1/channels").queryParam("slug[]", slug).build())
					.headers(headers -> copyHeaders(headers, token))
					.retrieve()
					.body(MAP_RESPONSE);
		}
		catch (RestClientResponseException ex) {
			throw BlazeApiException.from(ex);
		}
	}

	public Map<String, Object> getChatMessages(String channelId) {
		return getChatMessages(channelId, requireToken());
	}

	public Map<String, Object> getChatMessages(String channelId, TokenSnapshot token) {
		return getChatMessages(channelId, null, token);
	}

	public Map<String, Object> getChatMessages(String channelId, String cursor, TokenSnapshot token) {
		if (!properties.isApiConfigured()) {
			throw new ConfigurationMissingException("Blaze API is not configured");
		}
		if (token == null || token.accessTokenBlank()) {
			throw new ConfigurationMissingException("Blaze access token is not available");
		}
		if (channelId == null || channelId.isBlank()) {
			throw new IllegalArgumentException("channelId is required");
		}
		try {
			return restClient.get()
					.uri(uriBuilder -> {
						var request = uriBuilder.path("/v1/chats/messages")
								.queryParam("channelId", channelId);
						if (cursor != null && !cursor.isBlank()) {
							return request
									.queryParam("cursor", "{cursor}")
									.queryParam("limit", properties.getChatMessageLimit())
									.build(Map.of("cursor", cursor));
						}
						return request.queryParam("limit", properties.getChatMessageLimit()).build();
					})
					.headers(headers -> copyHeaders(headers, token))
					.retrieve()
					.body(MAP_RESPONSE);
		}
		catch (RestClientResponseException ex) {
			throw BlazeApiException.from(ex);
		}
	}

	private Map<String, Object> get(String path, TokenSnapshot token) {
		try {
			return restClient.get()
					.uri(path)
					.headers(headers -> copyHeaders(headers, token))
					.retrieve()
					.body(MAP_RESPONSE);
		}
		catch (RestClientResponseException ex) {
			throw BlazeApiException.from(ex);
		}
	}

	private TokenSnapshot requireToken() {
		if (!properties.isApiConfigured()) {
			throw new ConfigurationMissingException("Blaze API is not configured");
		}
		return tokenStore.current()
				.filter(token -> !token.accessTokenBlank())
				.orElseThrow(() -> new ConfigurationMissingException("Blaze access token is not available"));
	}

	private void copyHeaders(HttpHeaders headers, TokenSnapshot token) {
		headers.addAll(apiHeaders.authenticatedHeaders(token));
	}
}
