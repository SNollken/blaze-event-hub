package com.nollen.blaze.overlays;

import java.util.List;

public record OverlayManifestResponse(
		boolean enabled,
		String overlayId,
		String name,
		String publicToken,
		OverlayConfig config,
		List<OverlayLayer> layers,
		List<ManifestAsset> assets) {

	public record ManifestAsset(String id, String mimeType, String publicUrl) {}

	public static OverlayManifestResponse from(Overlay overlay) {
		List<ManifestAsset> assets = overlay.enabled()
				? overlay.assets().stream()
						.map(a -> new ManifestAsset(a.id(), a.mimeType(),
								"/api/public/overlays/" + overlay.publicToken() + "/assets/" + a.id()))
						.toList()
				: List.of();
		return new OverlayManifestResponse(
				overlay.enabled(),
				overlay.id(),
				overlay.name(),
				overlay.publicToken(),
				overlay.config(),
				overlay.enabled() ? overlay.layers() : List.of(),
				assets);
	}
}
