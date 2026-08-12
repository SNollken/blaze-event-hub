package com.blaze.eventhub.oauth;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TokenSnapshot(
		String type,
		String userId,
		String tokenType,
		@JsonIgnore String accessToken,
		@JsonIgnore String refreshToken,
		Instant expiresAt,
		List<String> scopes,
		Instant updatedAt) {

	public boolean accessTokenBlank() {
		return accessToken == null || accessToken.isBlank();
	}

	public boolean refreshTokenBlank() {
		return refreshToken == null || refreshToken.isBlank();
	}
}
