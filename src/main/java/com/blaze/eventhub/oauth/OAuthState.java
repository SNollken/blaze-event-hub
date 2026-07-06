package com.blaze.eventhub.oauth;

import java.time.Instant;

public record OAuthState(
		String state,
		String codeVerifier,
		Instant createdAt) {
}
