package com.blaze.eventhub.events;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.common.ConfigurationMissingException;
import com.blaze.eventhub.common.IdGenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlazeEventsRunnerTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void sessionWelcomeUpdatesSessionId() {
		NoopBlazeEventsClient client = new NoopBlazeEventsClient();
		BlazeEventsRunner runner = new BlazeEventsRunner(client, clock);

		runner.start();
		runner.acceptEnvelope(new BlazeEventEnvelope(
				Map.of("messageType", "session_welcome"),
				Map.of("session", Map.of("id", "session-123"))));

		assertThat(runner.status().runnerRunning()).isTrue();
		assertThat(runner.currentSessionId()).isEqualTo("session-123");
	}

	@Test
	void unknownEventsDoNotBreakRunner() {
		BlazeEventsRunner runner = new BlazeEventsRunner(new NoopBlazeEventsClient(), clock);

		runner.acceptEnvelope(new BlazeEventEnvelope(Map.of("messageType", "new_future_event"), Map.of()));

		assertThat(runner.status().lastMessageType()).isEqualTo("new_future_event");
	}

	@Test
	void subscriptionsUseCurrentSessionId() {
		BlazeEventsRunner runner = new BlazeEventsRunner(new NoopBlazeEventsClient(), clock);
		runner.acceptEnvelope(new BlazeEventEnvelope(Map.of("messageType", "session_welcome"), Map.of("sessionId", "session-abc")));
		EventSubscriptionService service = new EventSubscriptionService(runner, new InMemoryEventSubscriptionStore(), new IdGenerator(), clock);

		List<EventSubscriptionSnapshot> snapshots = service.sync(new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE, "1", "channel-1"));

		assertThat(snapshots).hasSize(1);
		assertThat(snapshots.getFirst().sessionId()).isEqualTo("session-abc");
	}

	@Test
	void syncRequiresSessionId() {
		EventSubscriptionService service = new EventSubscriptionService(
				new BlazeEventsRunner(new NoopBlazeEventsClient(), clock),
				new InMemoryEventSubscriptionStore(),
				new IdGenerator(),
				clock);

		assertThatThrownBy(() -> service.sync(new EventSubscriptionRequest(BlazeEventType.CHANNEL_FOLLOW, "1", "channel-1")))
				.isInstanceOf(ConfigurationMissingException.class);
	}

	@Test
	void stopShutsDownClient() {
		NoopBlazeEventsClient client = new NoopBlazeEventsClient();
		BlazeEventsRunner runner = new BlazeEventsRunner(client, clock);

		runner.start();
		runner.stop();

		assertThat(runner.status().runnerRunning()).isFalse();
		assertThat(client.isRunning()).isFalse();
	}
}
