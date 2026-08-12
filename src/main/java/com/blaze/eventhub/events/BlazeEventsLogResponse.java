package com.blaze.eventhub.events;

import java.util.List;

public record BlazeEventsLogResponse(
		long total,
		List<BlazeEventsLogEntry> entries) {
}
