package com.blaze.eventhub.oauth;

import java.time.Instant;

public record OAuthActionResponse(
		String status,
		boolean refreshed,
		boolean disconnected,
		boolean connected,
		boolean tokenPresent,
		boolean refreshCredentialPresent,
		boolean profilePresent,
		OAuthProfileResponse profile,
		Instant expiresAt,
		String nextRecommendedAction,
		String message) {
}
