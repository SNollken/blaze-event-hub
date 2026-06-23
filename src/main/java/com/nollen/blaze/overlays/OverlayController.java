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
public class OverlayController {

	private final OverlayService overlayService;

	public OverlayController(OverlayService overlayService) {
		this.overlayService = overlayService;
	}

	@GetMapping("/api/overlay-profiles/{profileId}/overlays")
	List<Overlay> list(@PathVariable String profileId) {
		return overlayService.listOverlays(profileId);
	}

	@PostMapping("/api/overlay-profiles/{profileId}/overlays")
	Overlay create(@PathVariable String profileId, @Valid @RequestBody CreateOverlayRequest request) {
		return overlayService.createOverlay(profileId, request);
	}

	@GetMapping("/api/overlays/{overlayId}")
	Overlay get(@PathVariable String overlayId) {
		return overlayService.getOverlay(overlayId);
	}

	@PutMapping("/api/overlays/{overlayId}")
	Overlay update(@PathVariable String overlayId, @Valid @RequestBody UpdateOverlayRequest request) {
		return overlayService.updateOverlay(overlayId, request);
	}

	@DeleteMapping("/api/overlays/{overlayId}")
	@ResponseStatus(NO_CONTENT)
	void delete(@PathVariable String overlayId) {
		overlayService.deleteOverlay(overlayId);
	}
}
