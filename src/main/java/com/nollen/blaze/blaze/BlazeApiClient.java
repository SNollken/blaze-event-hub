package com.nollen.blaze.blaze;

import java.util.Map;

import com.nollen.blaze.common.ConfigurationMissingException;
import com.nollen.blaze.config.BlazeProperties;
import com.nollen.blaze.oauth.TokenSnapshot;
import com.nollen.blaze.oauth.TokenStore;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
		TokenSnapshot token = requireToken();
		if (channelId == null || channelId.isBlank()) {
			throw new IllegalArgumentException("channelId is required");
		}
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/v1/chats/messages").queryParam("channelId", channelId).build())
					.headers(headers -> copyHeaders(headers, token))
					.retrieve()
					.body(MAP_RESPONSE);
		}
		catch (RestClientResponseException ex) {
			throw BlazeApiException.from(ex);
		}
	}

	public Map<String, Object> sendChatMessage(String channelId, String message) {
		TokenSnapshot token = requireToken();
		if (channelId == null || channelId.isBlank()) {
			throw new IllegalArgumentException("channelId is required");
		}
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message is required");
		}
		try {
			return restClient.post()
					.uri("/v1/chats/messages")
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> copyHeaders(headers, token))
					.body(Map.of("channelId", channelId, "message", message))
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
