package com.nollen.blaze.events;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.nollen.blaze.alert.AlertService;
import com.nollen.blaze.alert.EvaluateEventRequest;
import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.intake.LiveEventService;
import com.nollen.blaze.intake.LiveEventSource;
import com.nollen.blaze.intake.LiveEventType;

import org.springframework.stereotype.Component;

@Component
public class BlazeEventsPipeline {

	private final BlazeEventsLogStore logStore;
	private final IdGenerator idGenerator;
	private final Clock clock;
	private final AlertService alertService;
	private final LiveEventService liveEventService;

	public BlazeEventsPipeline(BlazeEventsLogStore logStore, IdGenerator idGenerator, Clock clock,
			AlertService alertService, LiveEventService liveEventService) {
		this.logStore = logStore;
		this.idGenerator = idGenerator;
		this.clock = clock;
		this.alertService = alertService;
		this.liveEventService = liveEventService;
	}

	public void acceptEnvelope(BlazeEventEnvelope envelope) {
		if (envelope == null) {
			return;
		}
		BlazeEventType eventType = resolveBlazeEventType(envelope.subscriptionType(), envelope.messageType());
		Map<String, Object> payload = envelope.payload() == null ? Map.of() : new HashMap<>(envelope.payload());
		dispatch(eventType, payload, "blaze:" + (envelope.sessionId() == null ? "" : envelope.sessionId()));
		logEntry(
				envelope.subscriptionType() != null ? envelope.subscriptionType() : "unknown",
				envelope.messageType() != null ? envelope.messageType() : "unknown",
				"Event received: " + (envelope.messageType() != null ? envelope.messageType() : "unknown"));
	}

	public BlazeEventsLogEntry simulate(String eventType, String message) {
		Instant now = Instant.now(clock);
		String resolvedType = eventType != null && !eventType.isBlank() ? eventType : "channel.chat.message";
		String resolvedMessage = message != null && !message.isBlank() ? message : "Simulated event: " + resolvedType;
		BlazeEventsLogEntry entry = new BlazeEventsLogEntry(
				idGenerator.newId(),
				now,
				resolvedType,
				"simulate",
				resolvedMessage,
				Map.of("simulated", true, "eventType", resolvedType, "timestamp", now.toString()).toString());
		logStore.append(entry);
		dispatch(resolveBlazeEventType(resolvedType, resolvedType), Map.of(
				"message", resolvedMessage,
				"simulated", true,
				"eventType", resolvedType,
				"timestamp", now.toString()), "simulate:" + resolvedType + ":" + now);
		return entry;
	}

	private void logEntry(String source, String eventType, String message) {
		BlazeEventsLogEntry entry = new BlazeEventsLogEntry(
				idGenerator.newId(),
				Instant.now(clock),
				eventType,
				source,
				message,
				null);
		logStore.append(entry);
	}

	private void dispatch(BlazeEventType eventType, Map<String, Object> payload, String dedupKey) {
		if (eventType == null) {
			return;
		}
		liveEventService.create(toLiveEventType(eventType), LiveEventSource.BLAZE_EVENT_PLACEHOLDER, payload, dedupKey);
		alertService.evaluateEvent(new EvaluateEventRequest(eventType, payload));
	}

	private static BlazeEventType resolveBlazeEventType(String subscriptionType, String messageType) {
		for (String candidate : new String[] { subscriptionType, messageType }) {
			if (candidate == null || candidate.isBlank()) {
				continue;
			}
			try {
				return BlazeEventType.from(candidate);
			}
			catch (IllegalArgumentException ignored) {
				// Non-subscription messages such as session_welcome are logged but not dispatched.
			}
		}
		return null;
	}

	private static LiveEventType toLiveEventType(BlazeEventType eventType) {
		return switch (eventType) {
		case CHANNEL_FOLLOW, CHANNEL_UNFOLLOW -> LiveEventType.FOLLOW;
		case CHANNEL_SUBSCRIBE, CHANNEL_SUBSCRIPTION_GIFT -> LiveEventType.SUBSCRIPTION;
		case CHANNEL_VOTE -> LiveEventType.VOTE;
		case CHANNEL_CHAT_MESSAGE, CHANNEL_CHAT_CLEAR, CHANNEL_CHAT_MESSAGE_DELETE -> LiveEventType.CHAT_MESSAGE;
		};
	}
}
