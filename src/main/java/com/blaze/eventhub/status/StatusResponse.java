package com.blaze.eventhub.status;

public record StatusResponse(
        String appName,
        String version,
        String javaVersion,
        boolean blazeOAuthConfigured,
        boolean blazeApiConfigured,
        long uptimeSeconds) {
}
