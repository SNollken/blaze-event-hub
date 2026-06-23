package com.nollen.blaze.blaze;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
		@NotBlank String channelId,
		@NotBlank @Size(max = 500) String message) {
}
