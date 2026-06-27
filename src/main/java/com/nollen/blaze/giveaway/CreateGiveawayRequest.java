package com.nollen.blaze.giveaway;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGiveawayRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 1000) String description,
		@Min(1) Integer maxEntries) {
}
