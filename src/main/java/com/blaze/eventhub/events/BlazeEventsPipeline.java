package com.blaze.eventhub.events;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.event.detection.ActionDetectionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BlazeEventsPipeline {

	private final BlazeEventsLogStore logStore;
	private final IdGenerator idGenerator;
	private final Clock clock;

	@Autowired(required = false)
	private ActionDetectionService actionDetectionService;

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

    @SuppressWarnings("unchecked")
    private void dispatchEventLogged(BlazeEventType eventType, Map<String, Object> payload) {
        if (payload == null) return;

        switch (eventType) {
            case CHANNEL_VOTE -> {
                if (actionDetectionService != null) {
                    try {
                        actionDetectionService.processActionEvent("vote", payload);
                        logEntry("blaze-events", eventType.id(), "Vote event dispatched to detection service");
                        return;
                    } catch (Exception e) {
                        logEntry("blaze-events", eventType.id(), "Vote detection error: " + e.getMessage());
                    }
                }
            }
            case CHANNEL_SUBSCRIBE -> {
                if (actionDetectionService != null) {
                    try {
                        actionDetectionService.processActionEvent("sub", payload);
                        logEntry("blaze-events", eventType.id(), "Subscribe event dispatched to detection service");
                        return;
                    } catch (Exception e) {
                        logEntry("blaze-events", eventType.id(), "Sub detection error: " + e.getMessage());
                    }
                }
            }
            case CHANNEL_SUBSCRIPTION_GIFT -> {
                if (actionDetectionService != null) {
                    try {
                        actionDetectionService.processActionEvent("gifted_sub", payload);
                        logEntry("blaze-events", eventType.id(), "Gifted sub event dispatched to detection service");
                        return;
                    } catch (Exception e) {
                        logEntry("blaze-events", eventType.id(), "Gifted sub detection error: " + e.getMessage());
                    }
                }
            }
        }
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
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }
}
