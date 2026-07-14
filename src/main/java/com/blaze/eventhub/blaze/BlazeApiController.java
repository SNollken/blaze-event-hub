package com.blaze.eventhub.blaze;

import jakarta.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/blaze")
public class BlazeApiController {

	private final BlazeChannelService channelService;

	public BlazeApiController(BlazeChannelService channelService) {
		this.channelService = channelService;
	}

	@GetMapping("/channels/resolve")
	BlazeChannelResponse resolveChannel(@RequestParam @NotBlank String slug) {
		return channelService.resolve(slug);
	}
}
