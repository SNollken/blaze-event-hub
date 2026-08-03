package com.nollen.blaze.events;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.ConfigurationMissingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSubscriptionServiceTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);
	private final IdGenerator idGenerator = new IdGenerator();
	private BlazeEventsRunner runner;
	private InMemoryEventSubscriptionStore store;
	private EventSubscriptionService service;

	@BeforeEach
	void setUp() {
		runner = new BlazeEventsRunner(new NoopBlazeEventsClient(), clock);
		store = new InMemoryEventSubscriptionStore();
		service = new EventSubscriptionService(runner, store, idGenerator, clock);
	}

	private void setSessionId(String sessionId) {
		runner.acceptEnvelope(new BlazeEventEnvelope(
				java.util.Map.of("messageType", "session_welcome"),
				java.util.Map.of("session", java.util.Map.of("id", sessionId))));
	}

	@Test
	void syncCreatesNewSubscription() {
		setSessionId("session-123");

		var snapshots = service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, "1", "channel-a"));

		assertThat(snapshots).hasSize(1);
		assertThat(snapshots.getFirst().type()).isEqualTo(BlazeEventType.CHANNEL_CHAT_MESSAGE);
		assertThat(snapshots.getFirst().channelId()).isEqualTo("channel-a");
		assertThat(snapshots.getFirst().sessionId()).isEqualTo("session-123");
		assertThat(snapshots.getFirst().version()).isEqualTo("1");
	}

	@Test
	void syncReplacesExistingSubscriptionForSameChannelAndType() {
		setSessionId("session-1");
		service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, "1", "channel-x"));

		setSessionId("session-2");
		var snapshots = service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, "2", "channel-x"));

		assertThat(snapshots).hasSize(1);
		assertThat(snapshots.getFirst().sessionId()).isEqualTo("session-2");
		assertThat(snapshots.getFirst().version()).isEqualTo("2");
	}

	@Test
	void syncDoesNotRemoveSubscriptionForDifferentType() {
		setSessionId("session-1");
		service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, "1", "channel-x"));

		setSessionId("session-2");
		var snapshots = service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_FOLLOW, "1", "channel-x"));

		assertThat(snapshots).hasSize(2);
		assertThat(snapshots).anyMatch(s -> s.type() == BlazeEventType.CHANNEL_CHAT_MESSAGE && s.sessionId().equals("session-1"));
		assertThat(snapshots).anyMatch(s -> s.type() == BlazeEventType.CHANNEL_FOLLOW && s.sessionId().equals("session-2"));
	}

	@Test
	void syncRejectsNullRequest() {
		assertThatThrownBy(() -> service.sync(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("required");
	}

	@Test
	void syncRejectsNullType() {
		setSessionId("session-1");
		assertThatThrownBy(() -> service.sync(new EventSubscriptionRequest(null, "1", "channel-x")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("type is required");
	}

	@Test
	void syncRejectsBlankChannelId() {
		setSessionId("session-1");
		assertThatThrownBy(() -> service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_FOLLOW, "1", "  ")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("channelId is required");
	}

	@Test
	void syncRequiresSessionId() {
		assertThatThrownBy(() -> service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_FOLLOW, "1", "channel-x")))
				.isInstanceOf(ConfigurationMissingException.class)
				.hasMessageContaining("session is not available");
	}

	@Test
	void syncNullVersionDefaultsToOne() {
		setSessionId("session-1");
		var snapshots = service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, null, "channel-a"));
		assertThat(snapshots.getFirst().version()).isEqualTo("1");
	}

	@Test
	void listReturnsAllSubscriptions() {
		setSessionId("session-1");
		service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, "1", "channel-a"));
		service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_FOLLOW, "1", "channel-b"));

		assertThat(service.list()).hasSize(2);
	}

	@Test
	void storeSaveFindUpsertOnEmptyStore() {
		assertThat(store.list()).isEmpty();
		assertThat(store.findByChannelIdAndType("ch", BlazeEventType.CHANNEL_FOLLOW)).isEmpty();
	}
}
