package com.nollen.blaze.alert;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertService service;

	public AlertController(AlertService service) {
		this.service = service;
	}

	@GetMapping("/active")
	List<Alert> activeAlerts() {
		return service.getActiveAlerts();
	}

	@GetMapping("/history")
	List<Alert> history(@RequestParam(required = false) String eventType) {
		return service.getAlertHistory(eventType);
	}

	@PostMapping("/acknowledge/{id}")
	Alert acknowledge(@PathVariable String id) {
		return service.acknowledge(id);
	}

	@PostMapping("/evaluate")
	List<Alert> evaluate(@RequestBody EvaluateEventRequest request) {
		return service.evaluateEvent(request);
	}

	@GetMapping("/stats")
	AlertStatsResponse stats() {
		return service.getStats();
	}

	@GetMapping("/capabilities")
java.util.Map<String, Object> capabilities() {
		return java.util.Map.of(
				"eventTypes", com.nollen.blaze.events.BlazeEventType.values(),
				"conditions", AlertCondition.values(),
				"maxAlerts", 1000);
	}
}
