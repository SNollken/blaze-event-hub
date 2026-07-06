package com.blaze.eventhub.oauth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthProfileServiceTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);
	private final InMemoryOAuthProfileStore store = new InMemoryOAuthProfileStore();

	@Test
	void synchronizesOnlySafeProfileSummaryFields() {
		OAuthProfileClient client = () -> Map.of(
				"data", Map.of(
						"id", "user-123456789",
						"username", "sofia",
						"display_name", "Sofia Blaze",
						"avatar_url", "https://cdn.example.test/avatar.png",
						"accessToken", "must-not-be-stored"));
		OAuthProfileService service = new OAuthProfileService(client, store, clock);

		OAuthProfileSyncResult result = service.synchronizeCurrentUser();

		assertThat(result.profilePresent()).isTrue();
		assertThat(store.current()).isPresent();
		assertThat(store.current().orElseThrow().id()).isEqualTo("user-123456789");
		assertThat(store.current().orElseThrow().displayName()).isEqualTo("Sofia Blaze");
		assertThat(store.current().orElseThrow().avatarUrl()).isEqualTo("https://cdn.example.test/avatar.png");
		assertThat(store.current().orElseThrow().syncedAt()).isEqualTo(Instant.parse("2026-06-23T12:00:00Z"));
		assertThat(store.current().orElseThrow().toString()).doesNotContain("must-not-be-stored", "accessToken");
	}

	@Test
	void profileFailureKeepsPreviousProfileAndReportsUnavailable() {
		OAuthProfileClient firstClient = () -> Map.of("id", "user-1", "displayName", "Sofia Blaze");
		OAuthProfileService service = new OAuthProfileService(firstClient, store, clock);
		service.synchronizeCurrentUser();

		OAuthProfileService failingService = new OAuthProfileService(() -> {
			throw new IllegalStateException("network unavailable");
		}, store, clock);
		OAuthProfileSyncResult result = failingService.synchronizeCurrentUser();

		assertThat(result.status()).isEqualTo("unavailable");
		assertThat(result.profilePresent()).isTrue();
		assertThat(result.profile().displayName()).isEqualTo("Sofia Blaze");
	}

	@Test
	void ignoresUnsafeAvatarUrl() {
		OAuthProfileClient client = () -> Map.of("id", "user-1", "avatarUrl", "javascript:alert(1)");
		OAuthProfileService service = new OAuthProfileService(client, store, clock);

		service.synchronizeCurrentUser();

		assertThat(store.current()).isPresent();
		assertThat(store.current().orElseThrow().avatarUrl()).isNull();
	}
}
