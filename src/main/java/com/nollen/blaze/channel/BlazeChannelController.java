package com.nollen.blaze.channel;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/blaze/channel")
public class BlazeChannelController {

	private final BlazeChannelService channelService;

	public BlazeChannelController(BlazeChannelService channelService) {
		this.channelService = channelService;
	}

	@GetMapping
	List<BlazeChannelConfig> list() {
		return channelService.list();
	}

	@GetMapping("/{id}")
	BlazeChannelConfig findById(@PathVariable String id) {
		return channelService.findById(id);
	}

	@PostMapping
	BlazeChannelConfig create(@Valid @RequestBody CreateBlazeChannelRequest request) {
		return channelService.create(request);
	}

	@PutMapping("/{id}")
	BlazeChannelConfig update(@PathVariable String id, @Valid @RequestBody CreateBlazeChannelRequest request) {
		return channelService.update(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable String id) {
		channelService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
