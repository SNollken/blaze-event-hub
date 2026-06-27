package com.nollen.blaze.overlays.runtime;

public record UpdateRuntimeOverlayConfigRequest(
		RuntimeOverlayType type,
		String name,
		Boolean enabled,
		Long refreshIntervalMs,
		String customCss,
		Integer positionX,
		Integer positionY,
		Integer positionWidth,
		Integer positionHeight,
		Double opacity) {
}
