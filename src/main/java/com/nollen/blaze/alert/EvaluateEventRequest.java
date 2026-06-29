package com.nollen.blaze.alert;

import com.nollen.blaze.events.BlazeEventType;
import java.util.Map;

public record EvaluateEventRequest(
		BlazeEventType eventType,
		Map<String, Object> payload) {
}
