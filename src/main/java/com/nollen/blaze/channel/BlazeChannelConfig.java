package com.nollen.blaze.channel;

import jakarta.validation.constraints.NotBlank;

public record BlazeChannelConfig(
		String id,
		@NotBlank String name,
		@NotBlank String channelId,
		String platform,
		boolean monitored) {

	public BlazeChannelConfig {
		if (platform == null || platform.isBlank()) {
			platform = "blaze";
		}
	}
}
