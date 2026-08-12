package com.blaze.eventhub.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.blaze.eventhub.alert.AlertService;
import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.intake.LiveEventDeduplicator;
import com.blaze.eventhub.intake.LiveEventNormalizer;
import com.blaze.eventhub.intake.LiveEventService;
import com.blaze.eventhub.intake.LiveEventStore;
import com.blaze.eventhub.intake.PayloadSanitizer;

class BlazeEventsPipelineTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);
	private final IdGenerator idGenerator = new IdGenerator();

	// Real intake side so dedup is exercised end-to-end (not mocked away).
	private final LiveEventStore store = new LiveEventStore();
	private final LiveEventService liveEventService = new LiveEventService(
			store, new LiveEventNormalizer(), new LiveEventDeduplicator(store),
			new PayloadSanitizer(), idGenerator, clock);
	private final AlertService alertService = mock(AlertService.class);
	private final BlazeEventsPipeline pipeline = new BlazeEventsPipeline(
			new BlazeEventsLogStore(), idGenerator, clock, alertService, liveEventService);

	@Test
	void distinctEventTypesInSameSessionAreNotCollapsed() {
		// Regression: dedupKey used to be "blaze:" + sessionId only, so all events from
		// one session shared one key and the 2nd+ were marked DUPLICATE (discarded).
		Map<String, Object> session = Map.of("session", Map.of("id", "session-abc"));
		pipeline.acceptEnvelope(new BlazeEventEnvelope(Map.of("messageType", "channel.follow"), session));
		pipeline.acceptEnvelope(new BlazeEventEnvelope(Map.of("messageType", "channel.subscribe"), session));

		List<String> statuses = store.listAll().stream()
				.map(e -> e.status().name()).toList();
		// Two events accepted — none collapsed into a duplicate.
		assertThat(store.count()).isEqualTo(2);
		assertThat(statuses).containsExactlyInAnyOrder("ACCEPTED", "ACCEPTED");
	}

	@Test
	void sameEventTypeRetransmissionInSameSessionIsDeduplicated() {
		Map<String, Object> session = Map.of("session", Map.of("id", "session-xyz"));
		pipeline.acceptEnvelope(new BlazeEventEnvelope(Map.of("messageType", "channel.follow"), session));
		// Same event type, same session, re-sent -> must be a real retransmission.
		pipeline.acceptEnvelope(new BlazeEventEnvelope(Map.of("messageType", "channel.follow"), session));

		assertThat(store.count()).isEqualTo(2);
		List<String> statuses = store.listAll().stream()
				.map(e -> e.status().name()).toList();
		assertThat(statuses).containsExactlyInAnyOrder("ACCEPTED", "DUPLICATE");
	}

	@Test
	void nonSubscriptionMessageIsNotDispatched() {
		// session_welcome is not a BlazeEventType -> no live event, no alert.
		pipeline.acceptEnvelope(new BlazeEventEnvelope(
				Map.of("messageType", "session_welcome"),
				Map.of("session", Map.of("id", "s1"))));
		assertThat(store.count()).isZero();
		verifyNoInteractions(alertService);
	}
}
