package com.nollen.blaze.overlays;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateOverlayRequest(
		@Size(max = 120) String name,
		@Size(max = 80) String type,
		Boolean enabled,
		@Valid OverlayConfig config) {
}
