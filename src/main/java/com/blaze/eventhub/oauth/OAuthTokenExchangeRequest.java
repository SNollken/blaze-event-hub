package com.blaze.eventhub.oauth;

public record OAuthTokenExchangeRequest(
		String clientId,
		String clientSecret,
		String code,
		String codeVerifier,
		String redirectUri,
		String grantType) {
}
