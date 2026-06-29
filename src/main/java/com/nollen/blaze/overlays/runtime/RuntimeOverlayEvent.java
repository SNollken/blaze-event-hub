package com.nollen.blaze.overlays.runtime;

import java.time.Instant;
import java.util.Map;

public record RuntimeOverlayEvent(
		RuntimeOverlayType type,
		String eventType,
		Map<String, Object> payload,
		Instant timestamp) {

	public static RuntimeOverlayEvent of(RuntimeOverlayType type, String eventType, Map<String, Object> payload) {
		return new RuntimeOverlayEvent(type, eventType, payload == null ? Map.of() : payload, Instant.now());
	}
}
