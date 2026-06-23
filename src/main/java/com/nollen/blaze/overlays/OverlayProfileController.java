package com.nollen.blaze.overlays;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/overlay-profiles")
public class OverlayProfileController {

	private final OverlayService overlayService;

	public OverlayProfileController(OverlayService overlayService) {
		this.overlayService = overlayService;
	}

	@GetMapping
	List<OverlayProfile> list() {
		return overlayService.listProfiles();
	}

	@PostMapping
	OverlayProfile create(@Valid @RequestBody CreateOverlayProfileRequest request) {
		return overlayService.createProfile(request);
	}

	@GetMapping("/{profileId}")
	OverlayProfile get(@PathVariable String profileId) {
		return overlayService.getProfile(profileId);
	}

	@PutMapping("/{profileId}")
	OverlayProfile update(@PathVariable String profileId, @Valid @RequestBody UpdateOverlayProfileRequest request) {
		return overlayService.updateProfile(profileId, request);
	}

	@DeleteMapping("/{profileId}")
	@ResponseStatus(NO_CONTENT)
	void delete(@PathVariable String profileId) {
		overlayService.deleteProfile(profileId);
	}
}
