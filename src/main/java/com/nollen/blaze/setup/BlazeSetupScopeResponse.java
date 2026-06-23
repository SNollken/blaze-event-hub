package com.nollen.blaze.setup;

public record BlazeSetupScopeResponse(
		String name,
		String phase,
		boolean requiredNow,
		String reason) {
}
