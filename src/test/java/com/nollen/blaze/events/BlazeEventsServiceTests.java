package com.nollen.blaze.events;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nollen.blaze.common.IdGenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlazeEventsServiceTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-27T12:00:00Z"), ZoneOffset.UTC);
	private final BlazeEventsRunner runner = new BlazeEventsRunner(new NoopBlazeEventsClient(), clock);
	private final BlazeEventsLogStore logStore = new BlazeEventsLogStore();
	private final BlazeEventsCapabilities capabilities = new BlazeEventsCapabilities(
			new com.nollen.blaze.config.BlazeProperties());
	private final IdGenerator idGenerator = new IdGenerator();
	private final BlazeEventsService service = new BlazeEventsService(runner, logStore, capabilities, idGenerator, clock);

	@Test
	void startEngineLogsEvent() {
		service.start();
		assertThat(runner.status().runnerRunning()).isTrue();
		assertThat(service.getLog(null, null, 10)).isNotEmpty();
		assertThat(service.getLog(null, null, 10).getFirst().eventType()).isEqualTo("ENGINE_START");
	}

	@Test
	void stopEngineLogsEvent() {
		service.start();
		service.stop();
		assertThat(runner.status().runnerRunning()).isFalse();
		assertThat(service.getLog(null, null, 10).stream()
				.anyMatch(e -> "ENGINE_STOP".equals(e.eventType()))).isTrue();
	}

	@Test
	void simulateCreatesLogEntry() {
		BlazeEventsLogEntry entry = service.simulate("channel.chat.message", "Hello world");
		assertThat(entry.eventType()).isEqualTo("channel.chat.message");
		assertThat(entry.source()).isEqualTo("simulate");
		assertThat(entry.message()).isEqualTo("Hello world");
	}

	@Test
	void simulateDefaultValues() {
		BlazeEventsLogEntry entry = service.simulate(null, null);
		assertThat(entry.eventType()).isEqualTo("channel.chat.message");
		assertThat(entry.message()).startsWith("Simulated event:");
	}

	@Test
	void statusIncludesEventCount() {
		service.simulate("channel.follow", "test");
		service.simulate("channel.subscribe", "test2");
		BlazeEventsStatusExtended status = service.status();
		assertThat(status.eventCount()).isEqualTo(2);
		assertThat(status.engineAvailable()).isTrue();
	}

	@Test
	void getLogFiltersByEventType() {
		service.simulate("channel.chat.message", "msg1");
		service.simulate("channel.follow", "msg2");
		var chatLogs = service.getLog("channel.chat.message", null, 10);
		assertThat(chatLogs).hasSize(1);
		assertThat(chatLogs.getFirst().eventType()).isEqualTo("channel.chat.message");
	}

	@Test
	void capabilitiesAreAvailable() {
		var caps = service.capabilities();
		assertThat(caps).containsKey("engine");
		assertThat(caps).containsKey("eventTypes");
		assertThat(caps).containsKey("features");
		assertThat(caps).containsKey("configuration");
	}

	@Test
	void acceptEnvelopeLogsEvent() {
		service.acceptEnvelope(new BlazeEventEnvelope(
				java.util.Map.of("messageType", "session_welcome"),
				java.util.Map.of("session", java.util.Map.of("id", "s1"))));
		assertThat(runner.currentSessionId()).isEqualTo("s1");
		assertThat(service.getLog(null, null, 10)).isNotEmpty();
	}
}
