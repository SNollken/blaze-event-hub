package com.nollen.blaze.overlays;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOverlayRequest(
		@NotBlank @Size(max = 120) String name,
		@NotBlank @Size(max = 80) String type,
		Boolean enabled,
		@Valid OverlayConfig config) {
}
