package com.nollen.blaze.overlays;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOverlayProfileRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description) {
}
