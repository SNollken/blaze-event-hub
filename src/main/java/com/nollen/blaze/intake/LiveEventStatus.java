package com.nollen.blaze.intake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LiveEventStatus {
	ACCEPTED("ACCEPTED"),
	DUPLICATE("DUPLICATE"),
	REJECTED("REJECTED"),
	NORMALIZED("NORMALIZED"),
	DISPATCH_PENDING("DISPATCH_PENDING"),
	DISPATCHED_PLACEHOLDER("DISPATCHED_PLACEHOLDER"),
	FAILED("FAILED");

	private final String id;

	LiveEventStatus(String id) {
		this.id = id;
	}

	@JsonValue
	public String id() {
		return id;
	}

	@JsonCreator
	public static LiveEventStatus from(String value) {
		for (LiveEventStatus status : values()) {
			if (status.id.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unsupported LiveEvent status: " + value);
	}
}
