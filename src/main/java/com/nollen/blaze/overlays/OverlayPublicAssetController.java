package com.nollen.blaze.overlays;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/overlays")
public class OverlayPublicAssetController {

	private final OverlayService overlayService;

	public OverlayPublicAssetController(OverlayService overlayService) {
		this.overlayService = overlayService;
	}

	@GetMapping("/{publicToken}/assets/{assetId}")
	ResponseEntity<byte[]> asset(@PathVariable String publicToken, @PathVariable String assetId) {
		byte[] bytes = overlayService.readPublicAsset(publicToken, assetId);
		String contentType = overlayService.assetMimeType(publicToken, assetId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.header("Cache-Control", "public, max-age=300")
				.body(bytes);
	}
}
