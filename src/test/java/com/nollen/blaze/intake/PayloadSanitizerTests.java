package com.nollen.blaze.intake;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadSanitizerTests {

	private PayloadSanitizer sanitizer;

	@BeforeEach
	void setUp() {
		sanitizer = new PayloadSanitizer();
	}

	@Test
	void sanitizesScriptTag() {
		Map<String, Object> payload = Map.of("message", "Hello <script>alert('xss')</script> world");
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertFalse(result.get("message").toString().contains("<script>"));
	}

	@Test
	void sanitizesEventHandlers() {
		Map<String, Object> payload = Map.of("message", "Hello onclick=alert(1) world");
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertFalse(result.get("message").toString().contains("onclick="));
	}

	@Test
	void sanitizesJavascriptUri() {
		Map<String, Object> payload = Map.of("url", "javascript:alert(1)");
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertFalse(result.get("url").toString().contains("javascript:"));
	}

	@Test
	void truncatesLongStrings() {
		String longStr = "a".repeat(3000);
		Map<String, Object> payload = Map.of("message", longStr);
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertTrue(result.get("message").toString().length() <= 2000);
	}

	@Test
	void handlesNullPayload() {
		Map<String, Object> result = sanitizer.sanitize(null);
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void preservesNormalPayload() {
		Map<String, Object> payload = Map.of("username", "testuser", "amount", 10.0);
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertEquals("testuser", result.get("username"));
		assertEquals(10.0, result.get("amount"));
	}

	@Test
	void detectsOversizePayload() {
		String hugeString = "x".repeat(20000);
		Map<String, Object> payload = Map.of("data", hugeString);
		assertTrue(sanitizer.isOversize(payload));
	}

	@Test
	void doesNotFlagNormalPayloadAsOversize() {
		Map<String, Object> payload = Map.of("message", "Hello world");
		assertFalse(sanitizer.isOversize(payload));
	}
}
