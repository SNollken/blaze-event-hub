package com.nollen.blaze.giveaway;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/giveaways")
public class GiveawayController {

	private final GiveawayService giveawayService;

	public GiveawayController(GiveawayService giveawayService) {
		this.giveawayService = giveawayService;
	}

	@GetMapping
	List<Giveaway> list() {
		return giveawayService.listGiveaways();
	}

	@PostMapping
	@ResponseStatus(CREATED)
	Giveaway create(@Valid @RequestBody CreateGiveawayRequest request) {
		return giveawayService.createGiveaway(request);
	}

	@GetMapping("/stats")
	GiveawayStatsResponse stats() {
		return giveawayService.getStats();
	}

	@GetMapping("/capabilities")
	GiveawayCapabilitiesResponse capabilities() {
		return GiveawayCapabilitiesResponse.defaults();
	}

	@GetMapping("/{id}")
	Giveaway get(@PathVariable String id) {
		return giveawayService.getGiveaway(id);
	}

	@PutMapping("/{id}")
	Giveaway update(@PathVariable String id, @Valid @RequestBody UpdateGiveawayRequest request) {
		return giveawayService.updateGiveaway(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(NO_CONTENT)
	void delete(@PathVariable String id) {
		giveawayService.deleteGiveaway(id);
	}

	@PostMapping("/{id}/open")
	Giveaway open(@PathVariable String id) {
		return giveawayService.openGiveaway(id);
	}

	@PostMapping("/{id}/close")
	Giveaway close(@PathVariable String id) {
		return giveawayService.closeGiveaway(id);
	}

	@PostMapping("/{id}/enter")
	GiveawayEntry enter(@PathVariable String id, @Valid @RequestBody EnterGiveawayRequest request) {
		return giveawayService.enterGiveaway(id, request);
	}

	@PostMapping("/{id}/draw")
	Giveaway draw(@PathVariable String id,
			@RequestParam(value = "winnerCount", defaultValue = "1") int winnerCount) {
		return giveawayService.drawWinners(id, winnerCount);
	}

	@GetMapping("/{id}/results")
	GiveawayResultsResponse results(@PathVariable String id) {
		return giveawayService.getResults(id);
	}
}
