package com.nollen.blaze.intake;

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
		Map<String, Object> cleaned = new java.util.LinkedHashMap<>(payload);
		for (Map.Entry<String, Object> entry : cleaned.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof String str) {
				entry.setValue(sanitizeString(str));
			}
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

	private String stripXss(String value) {
		return value
				.replaceAll("<script[^>]*>.*?</script>", "")
				.replaceAll("<script[^>]*/?>", "")
				.replaceAll("javascript:", "")
				.replaceAll("on\\w+\\s*=", "")
				.replaceAll("<iframe[^>]*>.*?</iframe>", "")
				.replaceAll("<iframe[^>]*/?>", "")
				.replaceAll("<object[^>]*>.*?</object>", "")
				.replaceAll("<embed[^>]*/?>", "")
				.replaceAll("<svg[^>]*on\\w+[^>]*>", "");
	}
}
