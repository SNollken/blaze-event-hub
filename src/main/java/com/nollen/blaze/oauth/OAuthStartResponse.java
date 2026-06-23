package com.nollen.blaze.oauth;

import java.util.List;

public record OAuthStartResponse(
		String authorizationUrl,
		String state,
		List<String> scopes) {
}
