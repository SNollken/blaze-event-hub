package com.nollen.blaze.status;

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
		long uptimeSeconds) {
}
