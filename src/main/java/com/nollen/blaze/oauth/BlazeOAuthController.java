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
	OAuthCallbackResponse callback(@RequestParam String code, @RequestParam String state) {
		return oAuthService.callback(code, state);
	}

	@PostMapping("/refresh")
	OAuthCallbackResponse refresh() {
		return oAuthService.refresh();
	}
}
