package com.nollen.blaze.intake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LiveEventType {
	FOLLOW("FOLLOW"),
	SUBSCRIPTION("SUBSCRIPTION"),
	DONATION("DONATION"),
	RAID("RAID"),
	VOTE("VOTE"),
	CHAT_MESSAGE("CHAT_MESSAGE"),
	GIVEAWAY_ENTRY("GIVEAWAY_ENTRY"),
	ALERT_TRIGGER("ALERT_TRIGGER"),
	CUSTOM("CUSTOM"),
	TEST("TEST");

	private final String id;

	LiveEventType(String id) {
		this.id = id;
	}

	@JsonValue
	public String id() {
		return id;
	}

	@JsonCreator
	public static LiveEventType from(String value) {
		for (LiveEventType type : values()) {
			if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported LiveEvent type: " + value);
	}
}
