package com.nollen.blaze.events;

import java.util.List;

public record BlazeEventsLogResponse(
		long total,
		List<BlazeEventsLogEntry> entries) {
}
