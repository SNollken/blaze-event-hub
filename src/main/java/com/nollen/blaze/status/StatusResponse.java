package com.nollen.blaze.status;

public record StatusResponse(
		String appName,
		String version,
		String javaVersion,
		boolean blazeOAuthConfigured,
		boolean blazeApiConfigured,
		boolean socketConfigured,
		boolean tokenPresent,
		boolean refreshTokenPresent,
		boolean monitoredChannelConfigured,
		long activeProfilesCount,
		long overlaysCount,
		long uptimeSeconds) {
}
