package com.nollen.blaze.channel;

import jakarta.validation.constraints.NotBlank;

public record CreateBlazeChannelRequest(
		@NotBlank String name,
		@NotBlank String channelId,
		String platform,
		boolean monitored) {
}
