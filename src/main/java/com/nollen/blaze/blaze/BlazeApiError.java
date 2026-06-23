package com.nollen.blaze.blaze;

public record BlazeApiError(
		int status,
		String message,
		String responseBody) {
}
