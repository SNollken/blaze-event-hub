package com.nollen.blaze.events;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nollen.blaze.common.ConfigurationMissingException;
import com.nollen.blaze.config.BlazeProperties;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blaze/events")
public class BlazeEventsController {

	private final BlazeEventsRunner runner;
	private final EventSubscriptionService subscriptionService;
	private final BlazeProperties blazeProperties;
	private final ObjectMapper objectMapper;

	public BlazeEventsController(BlazeEventsRunner runner, EventSubscriptionService subscriptionService,
			BlazeProperties blazeProperties, ObjectMapper objectMapper) {
		this.runner = runner;
		this.subscriptionService = subscriptionService;
		this.blazeProperties = blazeProperties;
		this.objectMapper = objectMapper;
	}

	@GetMapping("/status")
	BlazeEventsStatusResponse status() {
		return runner.status();
	}

	@PostMapping("/start")
	BlazeEventsStatusResponse start() {
		runner.start();
		return runner.status();
	}

	@PostMapping("/stop")
	BlazeEventsStatusResponse stop() {
		runner.stop();
		return runner.status();
	}

	@PostMapping("/subscriptions/sync")
	List<EventSubscriptionSnapshot> sync(@RequestBody(required = false) String requestBody) {
		return subscriptionService.sync(parseRequest(requestBody));
	}

	private EventSubscriptionRequest parseRequest(String requestBody) {
		if (requestBody == null || requestBody.isBlank()) {
			return defaultSyncRequest();
		}
		try {
			return objectMapper.readValue(requestBody, EventSubscriptionRequest.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Invalid Blaze Events subscription request");
		}
	}

	private EventSubscriptionRequest defaultSyncRequest() {
		if (!blazeProperties.isMonitoredChannelConfigured()) {
			throw new ConfigurationMissingException("Blaze monitored channel is not configured");
		}
		return new EventSubscriptionRequest(
				BlazeEventType.CHANNEL_CHAT_MESSAGE,
				"1",
				blazeProperties.getMonitoredChannelId());
	}
}
