package com.blaze.eventhub.events;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.blaze.eventhub.common.IdGenerator;

import org.springframework.stereotype.Component;

@Component
public class BlazeEventsPipeline {

	private final BlazeEventsLogStore logStore;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public BlazeEventsPipeline(BlazeEventsLogStore logStore, IdGenerator idGenerator, Clock clock) {
		this.logStore = logStore;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public void acceptEnvelope(BlazeEventEnvelope envelope) {
		if (envelope == null) {
			return;
		}
		BlazeEventType eventType = resolveBlazeEventType(envelope.subscriptionType(), envelope.messageType());
		logEntry(
				envelope.subscriptionType() != null ? envelope.subscriptionType() : "unknown",
				envelope.messageType() != null ? envelope.messageType() : "unknown",
				"Event received: " + (envelope.messageType() != null ? envelope.messageType() : "unknown"));
		// Dispatch will be implemented when detection module is ready (FASE 5)
		if (eventType != null) {
			dispatchEventLogged(eventType, envelope.payload());
		}
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
		dispatchEventLogged(resolveBlazeEventType(resolvedType, resolvedType), Map.of(
				"message", resolvedMessage,
				"simulated", true,
				"eventType", resolvedType,
				"timestamp", now.toString()));
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

	/**
	 * Placeholder dispatch — será substituído pelo VoteDetectionService na FASE 5.
	 * Por enquanto só loga o evento recebido.
	 */
	private void dispatchEventLogged(BlazeEventType eventType, Map<String, Object> payload) {
		String details = payload != null ? new HashMap<>(payload).toString() : "{}";
		logEntry("blaze-events", eventType.id(), "Dispatched: " + eventType.id() + " " + truncate(details, 500));
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

	private static String truncate(String value, int max) {
		if (value == null || value.length() <= max) return value;
		return value.substring(0, max) + "...";
	}
}
