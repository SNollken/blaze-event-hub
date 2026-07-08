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

	@GetMapping("/channels/resolve")
	Map<String, Object> resolveChannel(@RequestParam @NotBlank String slug) {
		Map<String, Object> response = apiClient.getChannelsBySlug(slug);
		return extractFirstChannel(response);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> extractFirstChannel(Map<String, Object> response) {
		if (response == null) {
			throw new IllegalArgumentException("Channel not found for slug");
		}
		Object data = response.get("data");
		if (!(data instanceof Map)) {
			throw new IllegalArgumentException("Channel not found for slug");
		}
		Map<String, Object> dataMap = (Map<String, Object>) data;
		Object rows = dataMap.get("rows");
		if (!(rows instanceof java.util.List<?>)) {
			throw new IllegalArgumentException("Channel not found for slug");
		}
		java.util.List<Map<String, Object>> rowsList = (java.util.List<Map<String, Object>>) rows;
		if (rowsList.isEmpty()) {
			throw new IllegalArgumentException("Channel not found for slug");
		}
		Map<String, Object> channel = rowsList.get(0);
		return Map.of(
				"id", channel.get("id"),
				"slug", channel.get("slug"),
				"displayName", channel.getOrDefault("displayName", ""),
				"avatarUrl", channel.getOrDefault("avatarUrl", ""));
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
