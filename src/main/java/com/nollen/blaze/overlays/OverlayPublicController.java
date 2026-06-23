package com.nollen.blaze.overlays;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/overlays")
public class OverlayPublicController {

	private final OverlayService overlayService;

	public OverlayPublicController(OverlayService overlayService) {
		this.overlayService = overlayService;
	}

	@GetMapping("/{publicToken}/manifest")
	OverlayManifestResponse manifest(@PathVariable String publicToken) {
		return overlayService.manifest(publicToken);
	}
}
