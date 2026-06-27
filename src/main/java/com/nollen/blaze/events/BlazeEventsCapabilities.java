package com.nollen.blaze.events;

import java.util.List;
import java.util.Map;

import com.nollen.blaze.config.BlazeProperties;

import org.springframework.stereotype.Component;

@Component
public class BlazeEventsCapabilities {

	private final BlazeProperties blazeProperties;

	public BlazeEventsCapabilities(BlazeProperties blazeProperties) {
		this.blazeProperties = blazeProperties;
	}

	public Map<String, Object> capabilities() {
		return Map.of(
				"engine", Map.of(
						"simulated", true,
						"realWebSocket", false,
						"description", "Blaze Events engine simulated - no real WebSocket connection"),
				"eventTypes", List.of(
						Map.of("id", "channel.follow", "description", "User followed a channel"),
						Map.of("id", "channel.unfollow", "description", "User unfollowed a channel"),
						Map.of("id", "channel.subscribe", "description", "User subscribed to a channel"),
						Map.of("id", "channel.subscription.gift", "description", "Subscription gift sent"),
						Map.of("id", "channel.vote", "description", "Channel vote event"),
						Map.of("id", "channel.chat.message", "description", "Chat message in channel"),
						Map.of("id", "channel.chat.clear", "description", "Chat cleared by moderator"),
						Map.of("id", "channel.chat.message_delete", "description", "Chat message deleted by moderator")),
				"features", Map.of(
						"simulate", true,
						"log", true,
						"startStop", true,
						"realConsumers", false),
				"configuration", Map.of(
						"socketConfigured", blazeProperties.isSocketConfigured(),
						"monitoredChannelConfigured", blazeProperties.isMonitoredChannelConfigured(),
						"socketUrl", blazeProperties.getSocketUrl(),
						"socketPath", blazeProperties.getSocketPath()));
	}
}
