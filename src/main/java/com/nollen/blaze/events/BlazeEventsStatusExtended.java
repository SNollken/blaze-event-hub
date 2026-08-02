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
		long messagesSeen,
		long acceptedEvents,
		long rejectedEvents) {

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
				base.messagesSeen(),
				base.acceptedEvents(),
				base.rejectedEvents());
	}
}
