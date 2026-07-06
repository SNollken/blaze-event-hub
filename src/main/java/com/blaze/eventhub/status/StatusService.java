package com.blaze.eventhub.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.events.BlazeEventsRunner;
import com.blaze.eventhub.events.BlazeEventsStatusResponse;
import com.blaze.eventhub.oauth.OAuthProfileStore;
import com.blaze.eventhub.oauth.OAuthProfileSummary;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.oauth.TokenStore;

import org.springframework.stereotype.Service;

@Service
public class StatusService {

	private static final String APP_NAME = "Blaze Event Hub";

	private final BlazeProperties blazeProperties;
	private final TokenStore tokenStore;
	private final OAuthProfileStore profileStore;
	private final BlazeEventsRunner eventsRunner;
	private final Clock clock;
	private final Instant startedAt;

	public StatusService(BlazeProperties blazeProperties, TokenStore tokenStore, OAuthProfileStore profileStore,
			BlazeEventsRunner eventsRunner, Clock clock) {
		this.blazeProperties = blazeProperties;
		this.tokenStore = tokenStore;
		this.profileStore = profileStore;
		this.eventsRunner = eventsRunner;
		this.clock = clock;
		this.startedAt = Instant.now(clock);
	}

	public StatusResponse currentStatus() {
		BlazeEventsStatusResponse eventsStatus = eventsRunner.status();
		TokenSnapshot token = tokenStore.current().orElse(null);
		OAuthProfileSummary profile = profileStore.current().orElse(null);
		boolean tokenPresent = token != null && !token.accessTokenBlank();
		boolean refreshCredentialPresent = token != null && !token.refreshTokenBlank();
		return new StatusResponse(
				APP_NAME,
				version(),
				System.getProperty("java.version"),
				blazeProperties.isOAuthConfigured(),
				blazeProperties.isApiConfigured(),
				blazeProperties.isSocketConfigured(),
				tokenPresent,
				refreshCredentialPresent,
				blazeProperties.isMonitoredChannelConfigured(),
				eventsStatus.runnerRunning(),
				eventsStatus.sessionId() != null && !eventsStatus.sessionId().isBlank(),
				0,  // overlay profiles (removed — no longer in scope)
				0,  // overlay count (removed — no longer in scope)
				Duration.between(startedAt, Instant.now(clock)).toSeconds(),
				tokenPresent,
				profile != null,
				profile == null ? null : displayName(profile),
				profile == null ? null : profile.id(),
				profile == null ? null : profile.syncedAt(),
				nextRecommendedAction(token, profile));
	}

	private String version() {
		String implementationVersion = StatusService.class.getPackage().getImplementationVersion();
		return implementationVersion == null ? "0.0.1-SNAPSHOT" : implementationVersion;
	}

	private String nextRecommendedAction(TokenSnapshot token, OAuthProfileSummary profile) {
		if (token == null || token.accessTokenBlank()) {
			return "CONNECT_BLAZE";
		}
		if (token.expiresAt() == null || token.expiresAt().isBefore(Instant.now(clock))) {
			return token.refreshTokenBlank() ? "RECONNECT_WITH_OFFLINE_ACCESS" : "REFRESH_SESSION";
		}
		if (profile == null) {
			return "SYNC_PROFILE_OR_REFRESH_SESSION";
		}
		return token.refreshTokenBlank() ? "RECONNECT_WITH_OFFLINE_ACCESS" : "READY_FOR_EVENTS";
	}

	private static String displayName(OAuthProfileSummary profile) {
		if (profile.displayName() != null && !profile.displayName().isBlank()) {
			return profile.displayName();
		}
		return profile.username();
	}
}
