package com.nollen.blaze.intake;

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
