package com.nollen.blaze.alert;

import java.util.List;

public record AlertStatsResponse(
		long totalRules,
		long enabledRules,
		long totalAlerts,
		long unacknowledgedAlerts,
		long acknowledgedAlerts,
		List<AlertRuleSnapshot> rules) {
}
