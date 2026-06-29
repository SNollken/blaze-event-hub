package com.nollen.blaze.overlays.runtime;

public record RuntimeOverlayConfig(
		String id,
		RuntimeOverlayType type,
		String name,
		boolean enabled,
		long refreshIntervalMs,
		String customCss,
		int positionX,
		int positionY,
		int positionWidth,
		int positionHeight,
		double opacity) {

	public static RuntimeOverlayConfig defaults(RuntimeOverlayType type) {
		return new RuntimeOverlayConfig(
				null,
				type,
				type.name().charAt(0) + type.name().substring(1).toLowerCase() + " Overlay",
				true,
				3000,
				"",
				0,
				0,
				400,
				200,
				1.0);
	}

	public RuntimeOverlayConfig withId(String newId) {
		return new RuntimeOverlayConfig(
				newId,
				type,
				name,
				enabled,
				refreshIntervalMs,
				customCss,
				positionX,
				positionY,
				positionWidth,
				positionHeight,
				opacity);
	}
}
