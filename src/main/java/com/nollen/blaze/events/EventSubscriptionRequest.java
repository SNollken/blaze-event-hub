package com.nollen.blaze.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventSubscriptionRequest(
		@NotNull BlazeEventType type,
		String version,
		@NotBlank String channelId) {

	public String effectiveVersion() {
		return version == null || version.isBlank() ? "1" : version;
	}
}
