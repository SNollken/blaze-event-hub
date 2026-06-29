package com.nollen.blaze.intake;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class LiveEventNormalizer {

	public Map<String, Object> normalize(LiveEventType type, LiveEventSource source, Map<String, Object> payload) {
		if (payload == null) {
			return Map.of();
		}
		Map<String, Object> normalized = new java.util.LinkedHashMap<>(payload);
		normalizeCommon(normalized);
		normalizeByType(type, normalized);
		return Map.copyOf(normalized);
	}

	private void normalizeCommon(Map<String, Object> data) {
		if (data.containsKey("username") && data.get("username") instanceof String username) {
			data.put("username", username.trim().toLowerCase(java.util.Locale.ROOT));
		}
		if (data.containsKey("displayName") && data.get("displayName") instanceof String displayName) {
			data.put("displayName", displayName.trim());
		}
		if (data.containsKey("amount")) {
			data.put("amount", normalizeAmount(data.get("amount")));
		}
	}

	private void normalizeByType(LiveEventType type, Map<String, Object> data) {
		switch (type) {
			case DONATION -> normalizeDonation(data);
			case CHAT_MESSAGE -> normalizeChatMessage(data);
			case RAID -> normalizeRaid(data);
			case VOTE -> normalizeVote(data);
			default -> {
				// no type-specific normalization needed
			}
		}
	}

	private void normalizeDonation(Map<String, Object> data) {
		if (data.containsKey("currency") && data.get("currency") instanceof String currency) {
			data.put("currency", currency.trim().toUpperCase(java.util.Locale.ROOT));
		}
	}

	private void normalizeChatMessage(Map<String, Object> data) {
		if (data.containsKey("message") && data.get("message") instanceof String message) {
			data.put("messageLength", message.length());
		}
	}

	private void normalizeRaid(Map<String, Object> data) {
		if (data.containsKey("raiderCount") && data.get("raiderCount") instanceof Number count) {
			data.put("raiderCount", Math.max(0, count.intValue()));
		}
	}

	private void normalizeVote(Map<String, Object> data) {
		if (data.containsKey("choice") && data.get("choice") instanceof String choice) {
			data.put("choice", choice.trim());
		}
	}

	private Object normalizeAmount(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String str) {
			try {
				return Double.parseDouble(str.trim());
			} catch (NumberFormatException e) {
				return 0.0;
			}
		}
		return 0.0;
	}
}
