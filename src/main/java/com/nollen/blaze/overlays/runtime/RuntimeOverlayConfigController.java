package com.nollen.blaze.overlays.runtime;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overlay-runtimes")
public class RuntimeOverlayConfigController {

	private final RuntimeOverlayConfigService service;

	public RuntimeOverlayConfigController(RuntimeOverlayConfigService service) {
		this.service = service;
	}

	@GetMapping
	List<RuntimeOverlayConfig> list() {
		return service.listAll();
	}

	@GetMapping("/{id}")
	RuntimeOverlayConfig get(@PathVariable String id) {
		return service.getById(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	RuntimeOverlayConfig create(@Valid @RequestBody CreateRuntimeOverlayConfigRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	RuntimeOverlayConfig update(@PathVariable String id, @Valid @RequestBody UpdateRuntimeOverlayConfigRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String id) {
		service.delete(id);
	}
}
