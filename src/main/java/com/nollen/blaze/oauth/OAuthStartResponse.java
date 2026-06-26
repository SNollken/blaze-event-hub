package com.nollen.blaze.oauth;

import java.util.List;

public record OAuthStartResponse(
		String authorizationUrl,
		List<String> scopes) {
}
