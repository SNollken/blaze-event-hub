package com.nollen.blaze.overlays;

import java.util.Map;

import jakarta.validation.constraints.Min;

public record UpdateOverlayLayerRequest(
		OverlayLayerType type,
		@Min(0) Integer x,
		@Min(0) Integer y,
		@Min(1) Integer width,
		@Min(1) Integer height,
		Integer zIndex,
		Boolean visible,
		Double opacity,
		String text,
		String assetId,
		Map<String, Object> style) {
}
