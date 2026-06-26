package com.nollen.blaze.status;

import java.time.Instant;

public record StatusResponse(
		String appName,
		String version,
		String javaVersion,
		boolean blazeOAuthConfigured,
		boolean blazeApiConfigured,
		boolean socketConfigured,
		boolean tokenPresent,
		boolean refreshCredentialPresent,
		boolean monitoredChannelConfigured,
		boolean eventsRunning,
		boolean sessionIdPresent,
		long activeProfilesCount,
		long overlaysCount,
		long uptimeSeconds,
		boolean oauthConnected,
		boolean profilePresent,
		String connectedAccountDisplayName,
		String connectedAccountId,
		Instant lastProfileSyncAt,
		String nextRecommendedAction) {
}
