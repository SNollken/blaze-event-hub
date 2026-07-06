package com.blaze.eventhub.oauth;

public record OAuthProfileResponse(
		String id,
		String username,
		String displayName,
		String avatarUrl,
		boolean rawAvailable) {

	public static OAuthProfileResponse from(OAuthProfileSummary profile) {
		if (profile == null) {
			return null;
		}
		return new OAuthProfileResponse(
				profile.id(),
				profile.username(),
				profile.displayName(),
				profile.avatarUrl(),
				false);
	}
}
