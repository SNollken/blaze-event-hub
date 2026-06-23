package com.nollen.blaze.overlays;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class OverlayAssetController {

	private final OverlayService overlayService;

	public OverlayAssetController(OverlayService overlayService) {
		this.overlayService = overlayService;
	}

	@PostMapping(path = "/api/overlays/{overlayId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	OverlayAsset upload(@PathVariable String overlayId, @RequestPart("file") MultipartFile file) throws IOException {
		return overlayService.addAsset(overlayId, file);
	}
}
