package com.blaze.eventhub.alert;

import java.time.Instant;
import java.util.Map;

import com.blaze.eventhub.events.BlazeEventType;

public record Alert(
		String id,
		String ruleId,
		String ruleName,
		BlazeEventType eventType,
		Instant triggeredAt,
		String message,
		boolean acknowledged,
		Map<String, Object> metadata) {

	public Alert acknowledge() {
		return new Alert(id, ruleId, ruleName, eventType, triggeredAt, message, true, metadata);
	}
}
