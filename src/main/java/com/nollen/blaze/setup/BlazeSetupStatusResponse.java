package com.nollen.blaze.setup;

import java.time.Instant;
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
		boolean oauthConnected,
		boolean profilePresent,
		String connectedAccountDisplayName,
		String connectedAccountId,
		Instant lastProfileSyncAt,
		String nextRecommendedAction,
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
