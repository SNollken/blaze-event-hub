package com.blaze.eventhub.blaze;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/blaze")
public class BlazeApiController {

	private final BlazeApiClient apiClient;

	public BlazeApiController(BlazeApiClient apiClient) {
		this.apiClient = apiClient;
	}

	@GetMapping("/users/profile")
	Map<String, Object> profile() {
		return apiClient.getCurrentUserProfile();
	}

	@GetMapping("/channels")
	Map<String, Object> channels(@RequestParam @NotBlank String slug) {
		return apiClient.getChannelsBySlug(slug);
	}

	@GetMapping("/chats/messages")
	Map<String, Object> chatMessages(@RequestParam @NotBlank String channelId) {
		return apiClient.getChatMessages(channelId);
	}

	@PostMapping("/chats/messages")
	Map<String, Object> sendMessage(@Valid @RequestBody SendChatMessageRequest request) {
		return apiClient.sendChatMessage(request.channelId(), request.message());
	}
}
