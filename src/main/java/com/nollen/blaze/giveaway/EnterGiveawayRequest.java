package com.nollen.blaze.giveaway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnterGiveawayRequest(
		@NotBlank @Size(max = 120) String participantName) {
}
