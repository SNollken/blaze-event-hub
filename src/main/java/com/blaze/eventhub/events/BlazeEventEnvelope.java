package com.blaze.eventhub.events;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlazeEventEnvelope(
		Map<String, Object> metadata,
		Map<String, Object> payload) {

	public String messageType() {
		return value(metadata, "messageType");
	}

	public String subscriptionType() {
		return value(metadata, "subscriptionType");
	}

	@SuppressWarnings("unchecked")
	public String sessionId() {
		String direct = value(payload, "sessionId");
		if (direct != null) {
			return direct;
		}
		Object session = payload == null ? null : payload.get("session");
		if (session instanceof Map<?, ?> sessionMap) {
			return value((Map<String, Object>) sessionMap, "id");
		}
		return null;
	}

	private static String value(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		return value == null ? null : value.toString();
	}
}
