package com.nollen.blaze.overlays;

import java.util.List;

public record OverlayManifestResponse(
		boolean enabled,
		String overlayId,
		String name,
		String publicToken,
		OverlayConfig config,
		List<OverlayLayer> layers,
		List<OverlayAsset> assets) {

	public static OverlayManifestResponse from(Overlay overlay) {
		return new OverlayManifestResponse(
				overlay.enabled(),
				overlay.id(),
				overlay.name(),
				overlay.publicToken(),
				overlay.config(),
				overlay.enabled() ? overlay.layers() : List.of(),
				overlay.enabled() ? overlay.assets() : List.of());
	}
}
