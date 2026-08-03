package com.nollen.blaze.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonDataTests {

	@Test
	void readMapParsesValidJson() {
		Map<String, Object> result = JsonData.readMap("{\"key\":\"value\",\"num\":42}");
		assertThat(result).containsEntry("key", "value");
		assertThat(result).containsEntry("num", 42);
	}

	@Test
	void readMapReturnsEmptyForCorruptedJson() {
		Map<String, Object> result = JsonData.readMap("{not valid json");
		assertThat(result).isEmpty();
	}

	@Test
	void readMapReturnsEmptyForNull() {
		assertThat(JsonData.readMap(null)).isEmpty();
	}

	@Test
	void readMapReturnsEmptyForBlank() {
		assertThat(JsonData.readMap("   ")).isEmpty();
	}

	@Test
	void readStringListParsesValidJson() {
		List<String> result = JsonData.readStringList("[\"a\",\"b\"]");
		assertThat(result).containsExactly("a", "b");
	}

	@Test
	void readStringListReturnsEmptyForCorruptedJson() {
		assertThat(JsonData.readStringList("{broken")).isEmpty();
	}

	@Test
	void readReturnsParsedValue() {
		Map<String, Object> result = JsonData.read("{\"x\":1}", new com.fasterxml.jackson.core.type.TypeReference<>() {
		}, Map.of());
		assertThat(result).containsEntry("x", 1);
	}

	@Test
	void readReturnsFallbackForCorruptedJson() {
		Map<String, Object> fallback = Map.of("fallback", true);
		Map<String, Object> result = JsonData.read("broken", new com.fasterxml.jackson.core.type.TypeReference<>() {
		}, fallback);
		assertThat(result).isSameAs(fallback);
	}

	@Test
	void readReturnsFallbackForNull() {
		Map<String, Object> fallback = Map.of("fallback", true);
		assertThat(JsonData.read(null, new com.fasterxml.jackson.core.type.TypeReference<>() {
		}, fallback)).isSameAs(fallback);
	}

	@Test
	void writeRoundTripsMap() {
		Map<String, Object> original = Map.of("key", "value", "num", 42);
		String json = JsonData.write(original);
		assertThat(JsonData.readMap(json)).containsAllEntriesOf(original);
	}
}
