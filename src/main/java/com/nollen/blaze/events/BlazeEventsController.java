package com.nollen.blaze.events;

import java.util.List;

import jakarta.validation.Valid;

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

	public BlazeEventsController(BlazeEventsRunner runner, EventSubscriptionService subscriptionService) {
		this.runner = runner;
		this.subscriptionService = subscriptionService;
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
	List<EventSubscriptionSnapshot> sync(@Valid @RequestBody EventSubscriptionRequest request) {
		return subscriptionService.sync(request);
	}
}
