package com.nollen.blaze.oauth;

import java.time.Instant;

public record OAuthState(
		String state,
		String codeVerifier,
		Instant createdAt) {
}
