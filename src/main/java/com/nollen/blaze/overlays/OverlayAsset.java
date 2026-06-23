package com.nollen.blaze.overlays;

import java.time.Instant;

public record OverlayAsset(
		String id,
		String overlayId,
		String originalFilename,
		String storedFilename,
		String mimeType,
		long sizeBytes,
		Integer width,
		Integer height,
		String checksum,
		Instant createdAt) {
}
