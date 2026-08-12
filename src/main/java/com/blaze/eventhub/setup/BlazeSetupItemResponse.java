package com.blaze.eventhub.setup;

public record BlazeSetupItemResponse(
		String code,
		String label,
		String status,
		String help) {
}
