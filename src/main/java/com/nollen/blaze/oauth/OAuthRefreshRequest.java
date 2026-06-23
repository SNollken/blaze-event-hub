package com.nollen.blaze.oauth;

public record OAuthRefreshRequest(
		String clientId,
		String clientSecret,
		String refreshToken) {
}
