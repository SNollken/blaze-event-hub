package com.blaze.eventhub.events;

public record SimulateBlazeEventRequest(
		String eventType,
		String message) {
}
