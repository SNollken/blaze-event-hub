package com.nollen.blaze.alert;

import java.util.Map;

import com.nollen.blaze.events.BlazeEventType;

public final class AlertEvaluator {

	private AlertEvaluator() {
	}

	public static boolean matches(AlertRule rule, BlazeEventType eventType, Map<String, Object> payload) {
		if (rule.eventType() != eventType) {
			return false;
		}
		return switch (rule.condition()) {
		case ALWAYS -> true;
		case MIN_AMOUNT -> meetsMinAmount(rule, payload);
		case RAID_MIN_SIZE -> meetsRaidMinSize(rule, payload);
		};
	}

	private static boolean meetsMinAmount(AlertRule rule, Map<String, Object> payload) {
		if (payload == null) {
			return false;
		}
		Object amountObj = payload.get("amount");
		if (amountObj == null) {
			return false;
		}
		double amount = toDouble(amountObj);
		return amount >= rule.threshold();
	}

	private static boolean meetsRaidMinSize(AlertRule rule, Map<String, Object> payload) {
		if (payload == null) {
			return false;
		}
		Object sizeObj = payload.get("size");
		if (sizeObj == null) {
			return false;
		}
		double size = toDouble(sizeObj);
		return size >= rule.threshold();
	}

	private static double toDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		try {
			return Double.parseDouble(value.toString());
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	public static String buildMessage(AlertRule rule, EvaluateEventRequest request) {
		String eventTypeLabel = request.eventType() != null ? request.eventType().id() : "unknown";
		return String.format(java.util.Locale.ROOT, "[ALERT] Rule '%s' triggered for %s (condition=%s, threshold=%.1f)",
				rule.name(), eventTypeLabel, rule.condition(), rule.threshold());
	}
}
