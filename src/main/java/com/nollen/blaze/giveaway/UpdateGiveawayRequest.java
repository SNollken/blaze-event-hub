package com.nollen.blaze.giveaway;

import jakarta.validation.constraints.Size;

public record UpdateGiveawayRequest(
		@Size(max = 200) String title,
		@Size(max = 1000) String description) {
}
