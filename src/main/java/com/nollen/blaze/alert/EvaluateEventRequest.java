package com.nollen.blaze.alert;

import com.nollen.blaze.events.BlazeEventType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EvaluateEventRequest(
		@NotNull BlazeEventType eventType,
		Map<String, Object> payload) {
}
