package com.nollen.blaze.overlays;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
public class OverlayLayerController {

	private final OverlayService overlayService;

	public OverlayLayerController(OverlayService overlayService) {
		this.overlayService = overlayService;
	}

	@GetMapping("/api/overlays/{overlayId}/layers")
	List<OverlayLayer> list(@PathVariable String overlayId) {
		return overlayService.listLayers(overlayId);
	}

	@PostMapping("/api/overlays/{overlayId}/layers")
	OverlayLayer create(@PathVariable String overlayId, @Valid @RequestBody CreateOverlayLayerRequest request) {
		return overlayService.createLayer(overlayId, request);
	}

	@PutMapping("/api/overlays/{overlayId}/layers/{layerId}")
	OverlayLayer update(@PathVariable String overlayId, @PathVariable String layerId,
			@Valid @RequestBody UpdateOverlayLayerRequest request) {
		return overlayService.updateLayer(overlayId, layerId, request);
	}

	@DeleteMapping("/api/overlays/{overlayId}/layers/{layerId}")
	@ResponseStatus(NO_CONTENT)
	void delete(@PathVariable String overlayId, @PathVariable String layerId) {
		overlayService.deleteLayer(overlayId, layerId);
	}
}
