package com.blaze.eventhub.intake;

public record LiveEventStats(
		long totalEvents,
		long acceptedCount,
		long duplicateCount,
		long rejectedCount,
		long normalizedCount,
		long dispatchPendingCount,
		long dispatchedPlaceholderCount,
		long failedCount) {
}
