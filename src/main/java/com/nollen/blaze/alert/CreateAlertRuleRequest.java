package com.nollen.blaze.alert;

import com.nollen.blaze.events.BlazeEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAlertRuleRequest(
		@NotBlank String name,
		@NotNull BlazeEventType eventType,
		@NotNull AlertCondition condition,
		double threshold,
		String template,
		boolean enabled,
		long cooldownMs) {

	public CreateAlertRuleRequest {
		if (threshold < 0) {
			threshold = 0;
		}
		if (cooldownMs < 0) {
			cooldownMs = 0;
		}
	}
}
