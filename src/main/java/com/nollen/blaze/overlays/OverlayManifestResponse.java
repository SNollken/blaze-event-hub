package com.nollen.blaze.overlays;

import java.util.List;
import java.util.Map;

public record OverlayManifestResponse(
		boolean enabled,
		String name,
		String publicToken,
		OverlayConfig config,
		List<ManifestLayer> layers,
		List<ManifestAsset> assets) {

	public record ManifestLayer(String id, OverlayLayerType type, int x, int y, int width, int height,
			int zIndex, boolean visible, double opacity, String text, String assetId, Map<String, Object> style) {
	}

	public record ManifestAsset(String id, String mimeType, String publicUrl) {}

	public static OverlayManifestResponse from(Overlay overlay) {
		List<ManifestAsset> assets = overlay.enabled()
				? overlay.assets().stream()
						.map(a -> new ManifestAsset(a.id(), a.mimeType(),
								"/api/public/overlays/" + overlay.publicToken() + "/assets/" + a.id()))
						.toList()
				: List.of();
		List<ManifestLayer> safeLayers = overlay.enabled()
				? overlay.layers().stream()
						.map(l -> new ManifestLayer(l.id(), l.type(), l.x(), l.y(), l.width(), l.height(),
								l.zIndex(), l.visible(), l.opacity(), l.text(), l.assetId(), l.style()))
						.toList()
				: List.of();
		return new OverlayManifestResponse(
				overlay.enabled(),
				overlay.name(),
				overlay.publicToken(),
				overlay.config(),
				safeLayers,
				assets);
	}
}
