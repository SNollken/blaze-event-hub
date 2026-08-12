package com.blaze.eventhub.alert;

import com.blaze.eventhub.events.BlazeEventType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EvaluateEventRequest(
		@NotNull BlazeEventType eventType,
		Map<String, Object> payload) {
}
