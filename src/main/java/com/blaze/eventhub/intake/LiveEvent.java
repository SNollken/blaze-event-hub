package com.blaze.eventhub.intake;

import java.time.Instant;
import java.util.Map;

public record LiveEvent(
		String id,
		LiveEventType type,
		LiveEventSource source,
		LiveEventStatus status,
		Map<String, Object> payload,
		Instant timestamp,
		String dedupKey) {
}
