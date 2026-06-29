package com.nollen.blaze.intake;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live-events")
public class LiveEventController {

	private final LiveEventService service;

	public LiveEventController(LiveEventService service) {
		this.service = service;
	}

	@GetMapping
	List<LiveEvent> list(
			@RequestParam(required = false) LiveEventType type,
			@RequestParam(required = false) LiveEventSource source,
			@RequestParam(required = false) LiveEventStatus status) {
		return service.listFiltered(type, source, status);
	}

	@PostMapping
	LiveEvent create(@RequestBody CreateLiveEventRequest request) {
		return service.create(request.type(), request.source(), request.payload(), request.dedupKey());
	}

	@GetMapping("/{id}")
	LiveEvent getById(@PathVariable String id) {
		return service.getById(id);
	}

	@GetMapping("/stats")
	LiveEventStats stats() {
		return service.stats();
	}

	@PostMapping("/simulate")
	LiveEvent simulate() {
		return service.simulate();
	}

	public record CreateLiveEventRequest(
			LiveEventType type,
			LiveEventSource source,
			Map<String, Object> payload,
			String dedupKey) {
	}
}
