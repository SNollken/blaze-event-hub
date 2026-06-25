package com.nollen.blaze.oauth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blaze/oauth")
public class BlazeOAuthController {

	private final BlazeOAuthService oAuthService;

	public BlazeOAuthController(BlazeOAuthService oAuthService) {
		this.oAuthService = oAuthService;
	}

	@PostMapping("/start")
	OAuthStartResponse start() {
		return oAuthService.start();
	}

	@GetMapping("/callback")
	OAuthCallbackResponse callback(@RequestParam(value = "code", required = false) String code,
			@RequestParam(value = "state", required = false) String state,
			@RequestParam(value = "error", required = false) String error,
			@RequestParam(value = "error_description", required = false) String errorDescription) {
		return oAuthService.callback(code, state, error, errorDescription);
	}

	@PostMapping("/refresh")
	OAuthCallbackResponse refresh() {
		return oAuthService.refresh();
	}
}
