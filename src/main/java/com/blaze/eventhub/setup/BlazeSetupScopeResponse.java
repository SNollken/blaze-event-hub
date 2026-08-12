package com.blaze.eventhub.setup;

public record BlazeSetupScopeResponse(
		String name,
		String phase,
		boolean requiredNow,
		String reason) {
}
