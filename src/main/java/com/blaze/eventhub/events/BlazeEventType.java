package com.blaze.eventhub.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BlazeEventType {
	CHANNEL_FOLLOW("channel.follow"),
	CHANNEL_UNFOLLOW("channel.unfollow"),
	CHANNEL_SUBSCRIBE("channel.subscribe"),
	CHANNEL_SUBSCRIPTION_GIFT("channel.subscription.gift"),
	CHANNEL_VOTE("channel.vote"),
	CHANNEL_CHAT_MESSAGE("channel.chat.message"),
	CHANNEL_CHAT_CLEAR("channel.chat.clear"),
	CHANNEL_CHAT_MESSAGE_DELETE("channel.chat.message_delete");

	private final String id;

	BlazeEventType(String id) {
		this.id = id;
	}

	@JsonValue
	public String id() {
		return id;
	}

	@JsonCreator
	public static BlazeEventType from(String value) {
		for (BlazeEventType type : values()) {
			if (type.id.equals(value) || type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported Blaze event type: " + value);
	}
}
