package com.nollen.blaze.alert;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts/rules")
public class AlertRuleController {

	private final AlertRuleService service;

	public AlertRuleController(AlertRuleService service) {
		this.service = service;
	}

	@GetMapping
	List<AlertRule> list(@RequestParam(required = false) String eventType) {
		List<AlertRule> rules = service.findAll();
		if (eventType != null && !eventType.isBlank()) {
			return rules.stream()
					.filter(r -> r.eventType() != null && r.eventType().id().equals(eventType))
					.toList();
		}
		return rules;
	}

	@PostMapping
	AlertRule create(@Valid @RequestBody CreateAlertRuleRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	AlertRule update(@PathVariable String id, @Valid @RequestBody UpdateAlertRuleRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	void delete(@PathVariable String id) {
		service.delete(id);
	}
}
