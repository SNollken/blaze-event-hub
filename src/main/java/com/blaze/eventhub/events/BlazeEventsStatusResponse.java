package com.blaze.eventhub.events;

import java.time.Instant;

public record BlazeEventsStatusResponse(
		boolean runnerRunning,
		boolean clientRunning,
		String sessionId,
		String lastMessageType,
		Instant startedAt) {
}
