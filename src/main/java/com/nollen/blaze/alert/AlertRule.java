package com.nollen.blaze.alert;

import java.time.Instant;

import com.nollen.blaze.events.BlazeEventType;

public record AlertRule(
		String id,
		String name,
		BlazeEventType eventType,
		AlertCondition condition,
		double threshold,
		String template,
		boolean enabled,
		long cooldownMs) {

	public AlertRule withUpdated(String name, BlazeEventType eventType, AlertCondition condition,
			double threshold, String template, boolean enabled, long cooldownMs) {
		return new AlertRule(this.id, name, eventType, condition, threshold, template, enabled, cooldownMs);
	}
}
