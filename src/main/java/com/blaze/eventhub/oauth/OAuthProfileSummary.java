package com.blaze.eventhub.oauth;

import java.time.Instant;

public record OAuthProfileSummary(
		String id,
		String username,
		String displayName,
		String avatarUrl,
		Instant syncedAt) {
}
