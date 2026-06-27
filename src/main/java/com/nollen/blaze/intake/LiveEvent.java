package com.nollen.blaze.intake;

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

	public LiveEvent withStatus(LiveEventStatus newStatus) {
		return new LiveEvent(id, type, source, newStatus, payload, timestamp, dedupKey);
	}

	public LiveEvent withPayload(Map<String, Object> newPayload) {
		return new LiveEvent(id, type, source, status, newPayload, timestamp, dedupKey);
	}
}
