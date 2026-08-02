package com.nollen.blaze.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PayloadSanitizerTests {

	private final PayloadSanitizer sanitizer = new PayloadSanitizer();

	@Test
	void nullPayloadReturnsEmptyMap() {
		assertThat(sanitizer.sanitize(null)).isEqualTo(Map.of());
	}

	@Test
	void topLevelScriptTagIsStripped() {
		Map<String, Object> payload = Map.of("message", "<script>alert(1)</script>hello");
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertThat(result.get("message")).isEqualTo("hello");
	}

	@Test
	void javascriptProtocolIsStripped() {
		Map<String, Object> payload = Map.of("url", "javascript:alert(1)");
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertThat(result.get("url")).isEqualTo("alert(1)");
	}

	@Test
	void nestedMapStringsAreSanitized() {
		Map<String, Object> payload = Map.of(
				"outer", Map.of("inner", "<script>alert(1)</script>bad"));
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertThat(result.get("outer")).isInstanceOf(Map.class);
		Map<?, ?> inner = (Map<?, ?>) result.get("outer");
		assertThat(inner.get("inner")).isEqualTo("bad");
	}

	@Test
	void nestedListStringsAreSanitized() {
		Map<String, Object> payload = Map.of(
				"tags", List.of("<script>evil</script>clean", "ok"));
		Map<String, Object> result = sanitizer.sanitize(payload);
		List<?> tags = (List<?>) result.get("tags");
		assertThat(tags).hasSize(2);
		assertThat(tags.get(0)).isEqualTo("clean");
		assertThat(tags.get(1)).isEqualTo("ok");
	}

	@Test
	void deeplyNestedXSSIsSanitized() {
		Map<String, Object> payload = Map.of(
				"data", Map.of(
						"user", Map.of("name", "javascript:alert(1)name")));
		Map<String, Object> result = sanitizer.sanitize(payload);
		Map<?, ?> data = (Map<?, ?>) result.get("data");
		Map<?, ?> user = (Map<?, ?>) data.get("user");
		assertThat(user.get("name")).isEqualTo("alert(1)name");
	}

	@Test
	void nonStringValueTypesArePreserved() {
		Map<String, Object> payload = Map.of(
				"amount", 100,
				"active", true,
				"ratio", 3.14,
				"name", "clean");
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertThat(result.get("amount")).isEqualTo(100);
		assertThat(result.get("active")).isEqualTo(true);
		assertThat(result.get("ratio")).isEqualTo(3.14);
		assertThat(result.get("name")).isEqualTo("clean");
	}

	@Test
	void nullValueBecomesEmpty() {
		Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("key", null);
		Map<String, Object> result = sanitizer.sanitize(payload);
		assertThat(result.get("key")).isEqualTo("");
	}

	@Test
	void oversizePayloadDetected() {
		String largeValue = "x".repeat(11_000);
		assertThat(sanitizer.isOversize(Map.of("data", largeValue))).isTrue();
	}

	@Test
	void smallPayloadNotOversize() {
		assertThat(sanitizer.isOversize(Map.of("data", "small"))).isFalse();
	}
}
