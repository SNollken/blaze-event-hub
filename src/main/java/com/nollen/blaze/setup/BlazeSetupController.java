package com.nollen.blaze.setup;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blaze/setup")
public class BlazeSetupController {

	private final BlazeSetupService setupService;

	public BlazeSetupController(BlazeSetupService setupService) {
		this.setupService = setupService;
	}

	@GetMapping
	BlazeSetupStatusResponse status() {
		return setupService.currentStatus();
	}
}
