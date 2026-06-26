package com.nollen.blaze.oauth;

import java.time.Instant;
import java.util.List;

public record OAuthSessionResponse(
		boolean connected,
		boolean tokenPresent,
		boolean refreshCredentialPresent,
		boolean profilePresent,
		OAuthProfileResponse profile,
		String tokenType,
		String userId,
		List<String> scopes,
		Instant expiresAt,
		boolean tokenExpiredOrUnknown,
		Instant lastConnectedAt,
		Instant lastProfileSyncAt,
		String nextRecommendedAction) {
}
