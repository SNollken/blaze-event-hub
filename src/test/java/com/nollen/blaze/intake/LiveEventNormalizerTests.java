package com.nollen.blaze.intake;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveEventNormalizerTests {

	private LiveEventNormalizer normalizer;

	@BeforeEach
	void setUp() {
		normalizer = new LiveEventNormalizer();
	}

	@Test
	void normalizesUsernameToLowercase() {
		Map<String, Object> payload = Map.of("username", "TestUser");
		Map<String, Object> result = normalizer.normalize(LiveEventType.FOLLOW, LiveEventSource.MANUAL, payload);
		assertEquals("testuser", result.get("username"));
	}

	@Test
	void normalizesAmountToDouble() {
		Map<String, Object> payload = Map.of("amount", "25.50");
		Map<String, Object> result = normalizer.normalize(LiveEventType.DONATION, LiveEventSource.MANUAL, payload);
		assertEquals(25.5, result.get("amount"));
	}

	@Test
	void normalizesCurrencyToUppercase() {
		Map<String, Object> payload = Map.of("currency", "brl");
		Map<String, Object> result = normalizer.normalize(LiveEventType.DONATION, LiveEventSource.MANUAL, payload);
		assertEquals("BRL", result.get("currency"));
	}

	@Test
	void normalizesRaidCountToNonNegative() {
		Map<String, Object> payload = Map.of("raiderCount", -5);
		Map<String, Object> result = normalizer.normalize(LiveEventType.RAID, LiveEventSource.MANUAL, payload);
		assertEquals(0, result.get("raiderCount"));
	}

	@Test
	void addsMessageLengthForChatMessage() {
		Map<String, Object> payload = Map.of("message", "Hello world!");
		Map<String, Object> result = normalizer.normalize(LiveEventType.CHAT_MESSAGE, LiveEventSource.MANUAL, payload);
		assertEquals(12, result.get("messageLength"));
	}

	@Test
	void handlesNullPayload() {
		Map<String, Object> result = normalizer.normalize(LiveEventType.CUSTOM, LiveEventSource.MANUAL, null);
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}
}
