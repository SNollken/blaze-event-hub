package com.nollen.blaze.overlays;

import java.util.Map;

public record OverlayLayer(
		String id,
		String overlayId,
		OverlayLayerType type,
		int x,
		int y,
		int width,
		int height,
		int zIndex,
		boolean visible,
		double opacity,
		String text,
		String assetId,
		Map<String, Object> style) {

	public OverlayLayer {
		style = style == null ? Map.of() : Map.copyOf(style);
	}
}
