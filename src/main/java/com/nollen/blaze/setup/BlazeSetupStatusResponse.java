package com.nollen.blaze.setup;

import java.util.List;

public record BlazeSetupStatusResponse(
		String appName,
		String environment,
		boolean clientIdConfigured,
		String clientIdMasked,
		boolean clientCredentialConfigured,
		boolean redirectUriConfigured,
		String redirectUri,
		List<String> requestedScopes,
		List<BlazeSetupScopeResponse> recommendedScopes,
		boolean tokenPresent,
		boolean tokenExpiredOrUnknown,
		boolean refreshCredentialPresent,
		boolean monitoredChannelConfigured,
		String monitoredChannel,
		boolean eventsConfigReady,
		boolean oauthStartReady,
		List<BlazeSetupItemResponse> checklist,
		List<BlazeSetupItemResponse> missingItems,
		List<String> nextSteps,
		List<BlazeSetupDocsLinkResponse> docsLinks,
		String envExample) {
}
