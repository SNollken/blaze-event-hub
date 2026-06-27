package com.nollen.blaze.giveaway;

import java.util.List;

public record GiveawayCapabilitiesResponse(
		String engine,
		String version,
		List<String> features,
		List<String> statuses,
		List<String> endpoints) {

	public static GiveawayCapabilitiesResponse defaults() {
		return new GiveawayCapabilitiesResponse(
				"Giveaway Engine MVP",
				"1.0.0",
				List.of(
						"Create and manage giveaways",
						"Open/close giveaway entries",
						"Enter participants by name",
						"Draw random winners",
						"View giveaway results",
						"Track giveaway statistics"),
				List.of("DRAFT", "OPEN", "CLOSED", "DRAWING", "COMPLETED", "CANCELLED"),
				List.of(
						"GET /api/giveaways",
						"POST /api/giveaways",
						"PUT /api/giveaways/{id}",
						"DELETE /api/giveaways/{id}",
						"GET /api/giveaways/{id}",
						"POST /api/giveaways/{id}/open",
						"POST /api/giveaways/{id}/close",
						"POST /api/giveaways/{id}/enter",
						"POST /api/giveaways/{id}/draw",
						"GET /api/giveaways/{id}/results",
						"GET /api/giveaways/stats",
						"GET /api/giveaways/capabilities"));
	}
}
