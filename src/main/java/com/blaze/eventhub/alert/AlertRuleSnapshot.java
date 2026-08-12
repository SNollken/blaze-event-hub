package com.blaze.eventhub.alert;

import com.blaze.eventhub.events.BlazeEventType;

public record AlertRuleSnapshot(
		String id,
		String name,
		BlazeEventType eventType,
		AlertCondition condition,
		double threshold,
		String template,
		boolean enabled,
		long cooldownMs) {
}
