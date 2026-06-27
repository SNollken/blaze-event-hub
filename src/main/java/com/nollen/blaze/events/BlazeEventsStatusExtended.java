package com.nollen.blaze.events;

import java.time.Instant;

public record BlazeEventsStatusExtended(
		boolean runnerRunning,
		boolean clientRunning,
		String sessionId,
		String lastMessageType,
		Instant startedAt,
		Instant lastEventReceivedAt,
		long eventCount,
		boolean engineAvailable) {

	public static BlazeEventsStatusExtended from(BlazeEventsRunner runner, Instant lastEventReceivedAt, long eventCount) {
		BlazeEventsStatusResponse base = runner.status();
		return new BlazeEventsStatusExtended(
				base.runnerRunning(),
				base.clientRunning(),
				base.sessionId(),
				base.lastMessageType(),
				base.startedAt(),
				lastEventReceivedAt,
				eventCount,
				true);
	}
}
