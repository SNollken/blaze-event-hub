package com.blaze.eventhub.oauth;

public record OAuthRefreshRequest(
		String clientId,
		String clientSecret,
		String refreshToken) {
}
