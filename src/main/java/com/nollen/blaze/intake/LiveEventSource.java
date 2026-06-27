package com.nollen.blaze.intake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LiveEventSource {
	MANUAL("MANUAL"),
	SIMULATED("SIMULATED"),
	BLAZE_EVENT_PLACEHOLDER("BLAZE_EVENT_PLACEHOLDER"),
	BLAZE_CHAT_PLACEHOLDER("BLAZE_CHAT_PLACEHOLDER"),
	BLAZE_EVENT_REAL_FUTURE("BLAZE_EVENT_REAL_FUTURE"),
	INTERNAL_TEST("INTERNAL_TEST");

	private final String id;

	LiveEventSource(String id) {
		this.id = id;
	}

	@JsonValue
	public String id() {
		return id;
	}

	@JsonCreator
	public static LiveEventSource from(String value) {
		for (LiveEventSource source : values()) {
			if (source.id.equalsIgnoreCase(value) || source.name().equalsIgnoreCase(value)) {
				return source;
			}
		}
		throw new IllegalArgumentException("Unsupported LiveEvent source: " + value);
	}
}
