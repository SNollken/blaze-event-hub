package com.nollen.blaze.overlays;

import java.time.Instant;

public record OverlayProfile(
		String id,
		String name,
		String description,
		Instant createdAt,
		Instant updatedAt) {
}
