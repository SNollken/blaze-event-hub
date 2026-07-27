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

	/**
	 * Valida campos obrigatórios manualmente (útil quando o parsing
	 * não passa pelo @Valid do Spring, ex: objectMapper.readValue).
	 */
	public void validate() {
		if (type == null) {
			throw new IllegalArgumentException("type is required");
		}
		if (channelId == null || channelId.isBlank()) {
			throw new IllegalArgumentException("channelId is required");
		}
	}
}
