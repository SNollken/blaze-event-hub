package com.nollen.blaze.oauth;

import java.util.List;

public record OAuthGenerateAuthUrlRequest(
		String clientId,
		String clientSecret,
		String redirectUri,
		List<String> scopes) {
}
