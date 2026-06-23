package com.nollen.blaze.overlays;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OverlayConfig(
		@Min(1) Integer canvasWidth,
		@Min(1) Integer canvasHeight,
		@Size(max = 40) String backgroundMode,
		@Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "must be a hex color") String backgroundColor,
		Boolean transparent,
		@Size(max = 120) String defaultFontFamily,
		@Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "must be a hex color") String defaultTextColor) {

	public static OverlayConfig defaultConfig() {
		return new OverlayConfig(1920, 1080, "solid", "#000000", true, "Inter, Arial, sans-serif", "#ffffff");
	}

	public OverlayConfig normalized() {
		OverlayConfig defaults = defaultConfig();
		return new OverlayConfig(
				canvasWidth == null ? defaults.canvasWidth : canvasWidth,
				canvasHeight == null ? defaults.canvasHeight : canvasHeight,
				blank(backgroundMode) ? defaults.backgroundMode : backgroundMode,
				blank(backgroundColor) ? defaults.backgroundColor : normalizeHex(backgroundColor),
				transparent == null ? defaults.transparent : transparent,
				blank(defaultFontFamily) ? defaults.defaultFontFamily : defaultFontFamily,
				blank(defaultTextColor) ? defaults.defaultTextColor : normalizeHex(defaultTextColor));
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String normalizeHex(String value) {
		return value.startsWith("#") ? value : "#" + value;
	}
}
