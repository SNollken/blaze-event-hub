package com.blaze.eventhub.setup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.events.BlazeEventEnvelope;
import com.blaze.eventhub.events.BlazeEventsRunner;
import com.blaze.eventhub.events.NoopBlazeEventsClient;
import com.blaze.eventhub.oauth.InMemoryOAuthProfileStore;
import com.blaze.eventhub.oauth.InMemoryTokenStore;
import com.blaze.eventhub.oauth.OAuthProfileSummary;
import com.blaze.eventhub.oauth.TokenSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlazeSetupServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private BlazeProperties props;
	private InMemoryTokenStore tokenStore;
	private InMemoryOAuthProfileStore profileStore;
	private BlazeEventsRunner runner;
	private BlazeSetupService service;

	@BeforeEach
	void setUp() {
		props = new BlazeProperties();
		tokenStore = new InMemoryTokenStore();
		profileStore = new InMemoryOAuthProfileStore();
		runner = new BlazeEventsRunner(new NoopBlazeEventsClient(), CLOCK);
		service = new BlazeSetupService(props, tokenStore, profileStore, runner, CLOCK);
	}

	private static TokenSnapshot token(String access, String refresh, Instant expiresAt) {
		return new TokenSnapshot("Bearer", "u", "Bearer", access, refresh, expiresAt, List.of(), NOW);
	}

	private void configureFullApp() {
		props.setClientId("client-id-1234567890");
		props.setClientSecret("secret-value");
		props.setMonitoredChannelId("channel-uuid-12345678");
	}

	@Test
	void freshChecklistHasNineOrderedItemsWithSevenMissing() {
		BlazeSetupStatusResponse r = service.currentStatus();
		assertThat(r.checklist()).extracting(BlazeSetupItemResponse::code).containsExactly(
				"client_id", "client_credential", "redirect_uri", "scopes", "token",
				"refresh_credential", "profile", "monitored_channel", "events");
		// defaults: redirectUri e scopes ja vem preenchidos em BlazeProperties
		assertThat(r.missingItems()).extracting(BlazeSetupItemResponse::code).containsExactly(
				"client_id", "client_credential", "token", "refresh_credential", "profile",
				"monitored_channel", "events");
		assertThat(r.oauthStartReady()).isFalse();
		assertThat(r.eventsConfigReady()).isFalse();
	}

	@Test
	void oauthStartReadyRequiresClientIdSecretAndRedirect() {
		assertThat(service.currentStatus().oauthStartReady()).isFalse();
		props.setClientId("cid");
		props.setClientSecret("secret");
		// redirectUri tem default nao-vazio
		assertThat(service.currentStatus().oauthStartReady()).isTrue();
		props.setRedirectUri("   ");
		assertThat(service.currentStatus().oauthStartReady()).isFalse();
	}

	@Test
	void eventsConfigReadyRequiresMonitoredChannelAndToken() {
		assertThat(service.currentStatus().eventsConfigReady()).isFalse();
		props.setMonitoredChannelId("chan-1");
		assertThat(service.currentStatus().eventsConfigReady()).isFalse();
		tokenStore.save(token("access-real", "refresh", NOW.plusSeconds(3600)));
		assertThat(service.currentStatus().eventsConfigReady()).isTrue();
	}

	@Test
	void clientIdMaskedUsesEllipsisForLongValues() {
		props.setClientId("abcdefghijklmnop");
		BlazeSetupStatusResponse r = service.currentStatus();
		assertThat(r.clientIdConfigured()).isTrue();
		assertThat(r.clientIdMasked()).isEqualTo("abcd...mnop");
	}

	@Test
	void clientIdMaskedUsesStarsForShortValuesAndNullWhenMissing() {
		props.setClientId("short");
		assertThat(service.currentStatus().clientIdMasked()).isEqualTo("***");

		props.setClientId("");
		BlazeSetupStatusResponse r = service.currentStatus();
		assertThat(r.clientIdConfigured()).isFalse();
		assertThat(r.clientIdMasked()).isNull();
	}

	@Test
	void monitoredChannelMaskedOnlyWhenConfigured() {
		assertThat(service.currentStatus().monitoredChannel()).isNull();
		props.setMonitoredChannelId("channel-uuid-12345678");
		BlazeSetupStatusResponse r = service.currentStatus();
		assertThat(r.monitoredChannelConfigured()).isTrue();
		assertThat(r.monitoredChannel()).isEqualTo("chan...5678");
	}

	@Test
	void environmentLocalForLocalhostAndConfiguredOtherwise() {
		assertThat(service.currentStatus().environment()).isEqualTo("local");
		props.setRedirectUri("https://beh.example.com/api/blaze/oauth/callback");
		assertThat(service.currentStatus().environment()).isEqualTo("configured");
	}

	@Test
	void envExampleUsesPlaceholdersAndCurrentRedirectUri() {
		String example = service.currentStatus().envExample();
		assertThat(example)
				.contains("BLAZE_CLIENT_ID=<cole_o_client_id_aqui>")
				.contains("BLAZE_CLIENT_SECRET=<cole_a_credencial_do_app_somente_no_backend>")
				.contains("BLAZE_REDIRECT_URI=http://localhost:8080/api/blaze/oauth/callback")
				.contains("BLAZE_SCOPES=users.read,offline.access")
				.contains("BLAZE_MONITORED_CHANNEL_ID=<opcional_uuid_do_canal>");

		props.setRedirectUri("https://beh.example.com/cb");
		assertThat(service.currentStatus().envExample())
				.contains("BLAZE_REDIRECT_URI=https://beh.example.com/cb");
	}

	@Test
	void tokenExpiredOrUnknownWithoutTokenOrBlankAccess() {
		assertThat(service.currentStatus().tokenExpiredOrUnknown()).isTrue();
		tokenStore.save(token("   ", "refresh", NOW.plusSeconds(3600)));
		assertThat(service.currentStatus().tokenExpiredOrUnknown()).isTrue();
	}

	@Test
	void tokenExpiredOrUnknownByExpiry() {
		tokenStore.save(token("access", "refresh", NOW.plusSeconds(3600)));
		assertThat(service.currentStatus().tokenExpiredOrUnknown()).isFalse();

		tokenStore.save(token("access", "refresh", NOW.minusSeconds(1)));
		assertThat(service.currentStatus().tokenExpiredOrUnknown()).isTrue();

		tokenStore.save(token("access", "refresh", null));
		assertThat(service.currentStatus().tokenExpiredOrUnknown()).isTrue();
	}

	@Test
	void nextRecommendedActionCoversAllBranches() {
		assertThat(service.currentStatus().nextRecommendedAction()).isEqualTo("CONNECT_BLAZE");

		tokenStore.save(token("access", "", NOW.minusSeconds(1)));
		assertThat(service.currentStatus().nextRecommendedAction())
				.isEqualTo("RECONNECT_WITH_OFFLINE_ACCESS");

		tokenStore.save(token("access", "refresh", NOW.minusSeconds(1)));
		assertThat(service.currentStatus().nextRecommendedAction()).isEqualTo("REFRESH_SESSION");

		tokenStore.save(token("access", "refresh", NOW.plusSeconds(3600)));
		assertThat(service.currentStatus().nextRecommendedAction())
				.isEqualTo("SYNC_PROFILE_OR_REFRESH_SESSION");

		profileStore.save(new OAuthProfileSummary("id1", "alice", "Alice", "https://x", NOW));
		assertThat(service.currentStatus().nextRecommendedAction()).isEqualTo("READY_FOR_EVENTS");

		tokenStore.save(token("access", "", NOW.plusSeconds(3600)));
		assertThat(service.currentStatus().nextRecommendedAction())
				.isEqualTo("RECONNECT_WITH_OFFLINE_ACCESS");
	}

	@Test
	void displayNameFallsBackToUsername() {
		tokenStore.save(token("access", "refresh", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", "  ", "https://x", NOW));
		assertThat(service.currentStatus().connectedAccountDisplayName()).isEqualTo("alice");

		profileStore.save(new OAuthProfileSummary("id1", "alice", null, "https://x", NOW));
		assertThat(service.currentStatus().connectedAccountDisplayName()).isEqualTo("alice");

		profileStore.save(new OAuthProfileSummary("id1", "alice", "Alice Live", "https://x", NOW));
		BlazeSetupStatusResponse r = service.currentStatus();
		assertThat(r.connectedAccountDisplayName()).isEqualTo("Alice Live");
		assertThat(r.connectedAccountId()).isEqualTo("id1");
		assertThat(r.lastProfileSyncAt()).isEqualTo(NOW);
	}

	@Test
	void nextStepsFreshSetupIncludesFullGuidance() {
		List<String> steps = service.currentStatus().nextSteps();
		assertThat(steps).hasSize(5);
		assertThat(steps.get(0)).contains("Blaze Developer Console");
		assertThat(steps).anySatisfy(s -> assertThat(s).contains("users.read,offline.access"));
		assertThat(steps).anySatisfy(s -> assertThat(s).contains("Iniciar OAuth"));
		assertThat(steps).anySatisfy(s -> assertThat(s).contains("BLAZE_MONITORED_CHANNEL_ID"));
		assertThat(steps).anySatisfy(s -> assertThat(s).contains("Events real"));
	}

	@Test
	void nextStepsShrinkToScopesWhenFullyConfiguredWithLiveSession() {
		configureFullApp();
		tokenStore.save(token("access", "refresh", NOW.plusSeconds(3600)));
		profileStore.save(new OAuthProfileSummary("id1", "alice", "Alice", "https://x", NOW));
		runner.acceptEnvelope(new BlazeEventEnvelope(
				Map.of("messageType", "session_welcome"), Map.of("sessionId", "sess-1")));

		List<String> steps = service.currentStatus().nextSteps();
		assertThat(steps).hasSize(1);
		assertThat(steps.get(0)).contains("users.read,offline.access");
	}

	@Test
	void recommendedScopesPreserveMinimalPrivilege() {
		BlazeSetupStatusResponse r = service.currentStatus();
		assertThat(r.requestedScopes()).containsExactly("users.read", "offline.access");
		assertThat(r.recommendedScopes()).extracting(BlazeSetupScopeResponse::name)
				.containsExactly("users.read", "offline.access", "channel.moderate", "users.bot");
		assertThat(r.recommendedScopes()).extracting(BlazeSetupScopeResponse::phase)
				.containsExactly("MVP_3", "MVP_3", "FUTURO_CHAT_MODERACAO", "FUTURO_CHAT_BOT");
		assertThat(r.recommendedScopes()).allSatisfy(s -> assertThat(s.requiredNow()).isFalse());
	}

	@Test
	void docsLinksPointToOfficialDevDocs() {
		List<BlazeSetupDocsLinkResponse> links = service.currentStatus().docsLinks();
		assertThat(links).hasSize(4);
		assertThat(links).allSatisfy(l -> assertThat(l.url()).startsWith("https://dev.blaze.stream"));
	}
}
