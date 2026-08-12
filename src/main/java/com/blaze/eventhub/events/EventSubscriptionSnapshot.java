package com.blaze.eventhub.events;

import java.time.Instant;

public record EventSubscriptionSnapshot(
		String id,
		BlazeEventType type,
		String version,
		String channelId,
		String sessionId,
		Instant createdAt) {
}
