package com.blaze.eventhub.blaze;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.oauth.TokenSnapshot;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class BlazeApiHeaders {

	private final BlazeProperties properties;

	public BlazeApiHeaders(BlazeProperties properties) {
		this.properties = properties;
	}

	public HttpHeaders authenticatedHeaders(TokenSnapshot token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token.accessToken());
		headers.add("client-id", properties.getClientId());
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		return headers;
	}
}
