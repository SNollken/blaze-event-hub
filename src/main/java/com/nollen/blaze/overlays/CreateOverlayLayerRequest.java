package com.nollen.blaze.overlays;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOverlayLayerRequest(
		@NotNull OverlayLayerType type,
		@Min(0) int x,
		@Min(0) int y,
		@Min(1) int width,
		@Min(1) int height,
		int zIndex,
		Boolean visible,
		Double opacity,
		String text,
		String assetId,
		Map<String, Object> style) {
}
