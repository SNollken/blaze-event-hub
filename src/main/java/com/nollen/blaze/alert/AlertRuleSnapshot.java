package com.nollen.blaze.alert;

import com.nollen.blaze.events.BlazeEventType;

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
