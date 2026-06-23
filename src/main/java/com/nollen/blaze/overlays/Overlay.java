package com.nollen.blaze.overlays;

import java.time.Instant;
import java.util.List;

public record Overlay(
		String id,
		String profileId,
		String name,
		String type,
		String publicToken,
		boolean enabled,
		OverlayConfig config,
		List<OverlayLayer> layers,
		List<OverlayAsset> assets,
		Instant createdAt,
		Instant updatedAt) {

	public Overlay {
		layers = layers == null ? List.of() : List.copyOf(layers);
		assets = assets == null ? List.of() : List.copyOf(assets);
		config = config == null ? OverlayConfig.defaultConfig() : config.normalized();
	}
}
