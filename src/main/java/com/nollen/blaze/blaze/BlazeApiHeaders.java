package com.nollen.blaze.blaze;

import java.time.Clock;

import com.nollen.blaze.common.OAuthException;
import com.nollen.blaze.config.BlazeProperties;
import com.nollen.blaze.oauth.TokenSnapshot;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class BlazeApiHeaders {

	private final BlazeProperties properties;
	private final Clock clock;

	public BlazeApiHeaders(BlazeProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public HttpHeaders authenticatedHeaders(TokenSnapshot token) {
		if (token.expiresAt() != null && clock.instant().isAfter(token.expiresAt())) {
			throw new OAuthException(401, "TOKEN_EXPIRED", "Token expired. Please refresh.");
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token.accessToken());
		headers.add("client-id", properties.getClientId());
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		return headers;
	}
}
