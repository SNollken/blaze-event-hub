package com.nollen.blaze.oauth;

public record OAuthTokenExchangeRequest(
		String clientId,
		String clientSecret,
		String code,
		String codeVerifier,
		String redirectUri,
		String grantType) {
}
