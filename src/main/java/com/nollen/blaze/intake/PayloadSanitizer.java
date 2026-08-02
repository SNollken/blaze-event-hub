package com.nollen.blaze.intake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class PayloadSanitizer {

	private static final int MAX_PAYLOAD_SIZE_BYTES = 10_000;
	private static final int MAX_STRING_LENGTH = 2_000;

	public Map<String, Object> sanitize(Map<String, Object> payload) {
		if (payload == null) {
			return Map.of();
		}
		Map<String, Object> cleaned = new LinkedHashMap<>(payload);
		for (Map.Entry<String, Object> entry : cleaned.entrySet()) {
			entry.setValue(sanitizeValue(entry.getValue()));
		}
		return Map.copyOf(cleaned);
	}

	public boolean isOversize(Map<String, Object> payload) {
		if (payload == null) {
			return false;
		}
		try {
			int estimatedSize = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
			return estimatedSize > MAX_PAYLOAD_SIZE_BYTES;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Recursively sanitizes any value: strings are cleaned, nested maps and
	 * lists are traversed so XSS payloads buried in nested structures are
	 * neutralized before storage in LiveEvent payloads.
	 */
	private Object sanitizeValue(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String str) {
			return sanitizeString(str);
		}
		if (value instanceof Map) {
			Map<String, Object> nested = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				nested.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue()));
			}
			return nested;
		}
		if (value instanceof List) {
			List<Object> sanitized = new ArrayList<>();
			for (Object item : (List<?>) value) {
				sanitized.add(sanitizeValue(item));
			}
			return sanitized;
		}
		return value;
	}

	private String sanitizeString(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.length() > MAX_STRING_LENGTH) {
			trimmed = trimmed.substring(0, MAX_STRING_LENGTH);
		}
		return stripXss(trimmed);
	}

	private static String stripXss(String value) {
		return value
				// Neutralize HTML numeric character references FIRST: &#106;avascript: -> harmless
				.replaceAll("&#", "&amp;#")
				.replaceAll("(?is)<script[^>]*>.*?</script>", "")
				.replaceAll("(?is)<script[^>]*/?>", "")
				.replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
				.replaceAll("(?i)<iframe[^>]*/?>", "")
				.replaceAll("(?is)<object[^>]*>.*?</object>", "")
				.replaceAll("(?is)<embed[^>]*/?>", "")
				.replaceAll("(?i)javascript\\s*:", "")
				.replaceAll("(?i)on\\w+\\s*=", "")
				.replaceAll("(?is)<svg[^>]*on\\w+[^>]*>", "");
	}
}
