package com.nollen.blaze.oauth;

import java.time.Instant;
import java.util.List;

public record OAuthCallbackResponse(
		String status,
		String tokenType,
		String userId,
		List<String> scopes,
		Instant expiresAt,
		boolean refreshTokenPresent) {
}
