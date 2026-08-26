package com.blaze.eventhub.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.events.BlazeEventsRunner;
import com.blaze.eventhub.events.NoopBlazeEventsClient;
import com.blaze.eventhub.oauth.InMemoryOAuthProfileStore;
import com.blaze.eventhub.oauth.InMemoryTokenStore;
import com.blaze.eventhub.oauth.OAuthProfileSummary;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.overlays.InMemoryOverlayRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private BlazeProperties props;
	private InMemoryTokenStore tokenStore;
	private InMemoryOAuthProfileStore profileStore;
	private InMemoryOverlayRepository overlayRepository;
	private StatusService service;

	@BeforeEach
	void setUp() {
		props = new BlazeProperties();
		props.setClientId("cid");
		props.setClientSecret("secret");
		props.setRedirectUri("http://localhost/cb");
		props.setApiBaseUrl("http://api");
		tokenStore = new InMemoryTokenStore();
		profileStore = new InMemoryOAuthProfileStore();
		overlayRepository = new InMemoryOverlayRepository();
		service = new StatusService(
				props,
				tokenStore,
				profileStore,
				overlayRepository,
				new BlazeEventsRunner(new NoopBlazeEventsClient(), CLOCK),
				CLOCK);
	}

	private static TokenSnapshot token(String access, String refresh, Instant expiresAt) {
		return new TokenSnapshot("Bearer", "u", "Bearer", access, refresh, expiresAt,
				List.of(), NOW);
	}

	@Test
	void noTokenRecommendsConnectBlaze() {
		StatusResponse r = service.currentStatus();
		assertThat(r.tokenPresent()).isFalse();
		assertThat(r.refreshCredentialPresent()).isFalse();
		assertThat(r.nextRecommendedAction()).isEqualTo("CONNECT_BLAZE");
	}

	@Test
	void blankAccessTokenRecommendsConnectBlaze() {
		tokenStore.save(token("   ", "refresh-real", NOW.plusSeconds(3600)));
		StatusResponse r = service.currentStatus();
		assertThat(r.tokenPresent()).isFalse();
		assertThat(r.nextRecommendedAction()).isEqualTo("CONNECT_BLAZE");
	}

	@Test
	void expiredTokenWithoutRefreshRecommendsReconnect() {
		tokenStore.save(token("access-real", "", NOW.minusSeconds(1)));
		StatusResponse r = service.currentStatus();
		assertThat(r.tokenPresent()).isTrue();
		assertThat(r.refreshCredentialPresent()).isFalse();
		assertThat(r.nextRecommendedAction()).isEqualTo("RECONNECT_WITH_OFFLINE_ACCESS");
	}

	@Test
	void expiredTokenWithRefreshRecommendsRefreshSession() {
		tokenStore.save(token("access-real", "refresh-real", NOW.minusSeconds(1)));
		StatusResponse r = service.currentStatus();
		assertThat(r.tokenPresent()).isTrue();
		assertThat(r.refreshCredentialPresent()).isTrue();
		assertThat(r.nextRecommendedAction()).isEqualTo("REFRESH_SESSION");
	}

	@Test
	void validTokenWithoutProfileRecommendsSync() {
		tokenStore.save(token("access-real", "refresh-real", NOW.plusSeconds(3600)));
		StatusResponse r = service.currentStatus();
		assertThat(r.profilePresent()).isFalse();
		assertThat(r.nextRecommendedAction()).isEqualTo("SYNC_PROFILE_OR_REFRESH_SESSION");
	}

	@Test
	void validTokenWithProfileAndRefreshRecommendsReadyForEvents() {
		tokenStore.save(token("access-real", "refresh-real", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", "Alice", "https://x", NOW));
		StatusResponse r = service.currentStatus();
		assertThat(r.profilePresent()).isTrue();
		assertThat(r.nextRecommendedAction()).isEqualTo("READY_FOR_EVENTS");
	}

	@Test
	void validTokenWithProfileButNoRefreshRecommendsReconnectForOffline() {
		tokenStore.save(token("access-real", "", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", "Alice", "https://x", NOW));
		StatusResponse r = service.currentStatus();
		assertThat(r.refreshCredentialPresent()).isFalse();
		assertThat(r.nextRecommendedAction()).isEqualTo("RECONNECT_WITH_OFFLINE_ACCESS");
	}

	@Test
	void displayNameFallsBackToUsernameWhenBlank() {
		tokenStore.save(token("a", "r", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", "", "https://x", NOW));
		StatusResponse r = service.currentStatus();
		assertThat(r.connectedAccountDisplayName()).isEqualTo("alice");
	}

	@Test
	void displayNameFallsBackToUsernameWhenNull() {
		tokenStore.save(token("a", "r", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", null, "https://x", NOW));
		StatusResponse r = service.currentStatus();
		assertThat(r.connectedAccountDisplayName()).isEqualTo("alice");
	}

	@Test
	void displayNameUsesDisplayNameWhenPresent() {
		tokenStore.save(token("a", "r", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", "Alice Live", "https://x", NOW));
		StatusResponse r = service.currentStatus();
		assertThat(r.connectedAccountDisplayName()).isEqualTo("Alice Live");
	}

	@Test
	void connectedAccountFieldsAreNullWhenNoProfile() {
		StatusResponse r = service.currentStatus();
		assertThat(r.connectedAccountDisplayName()).isNull();
		assertThat(r.connectedAccountId()).isNull();
		assertThat(r.lastProfileSyncAt()).isNull();
	}

	@Test
	void configFlagsReflectBlazeProperties() {
		StatusResponse r = service.currentStatus();
		assertThat(r.blazeOAuthConfigured()).isTrue();
		assertThat(r.blazeApiConfigured()).isTrue();
		// socketUrl/socketPath tem defaults nao-vazios em BlazeProperties
		assertThat(r.socketConfigured()).isTrue();
		assertThat(r.monitoredChannelConfigured()).isFalse();
		assertThat(r.appName()).isEqualTo("Blaze Event Hub");
	}
}
