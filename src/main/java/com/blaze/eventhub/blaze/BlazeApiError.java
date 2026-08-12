package com.blaze.eventhub.blaze;

public record BlazeApiError(
		int status,
		String message,
		String responseBody) {
}
