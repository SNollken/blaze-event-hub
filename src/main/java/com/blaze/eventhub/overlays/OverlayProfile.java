package com.blaze.eventhub.overlays;

import java.time.Instant;

public record OverlayProfile(
		String id,
		String name,
		String description,
		Instant createdAt,
		Instant updatedAt) {
}
