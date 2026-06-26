package com.nollen.blaze.oauth;

import java.time.Instant;

public record OAuthProfileSummary(
		String id,
		String username,
		String displayName,
		String avatarUrl,
		Instant syncedAt) {
}
