package com.nollen.blaze.events;

import java.time.Instant;

public record BlazeEventsLogEntry(
		String id,
		Instant timestamp,
		String eventType,
		String source,
		String message,
		String data) {
}
