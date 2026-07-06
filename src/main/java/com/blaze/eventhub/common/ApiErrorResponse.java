package com.blaze.eventhub.common;

import java.time.Instant;

public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path) {
}
