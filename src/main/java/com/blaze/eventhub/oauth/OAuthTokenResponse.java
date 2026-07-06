package com.blaze.eventhub.oauth;

import java.util.List;

public record OAuthTokenResponse(
		String type,
		String userId,
		String tokenType,
		String accessToken,
		String refreshToken,
		Long expiresIn,
		List<String> scopes) {
}
