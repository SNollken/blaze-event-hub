package com.blaze.eventhub.common;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonData {

	private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
	};

	private JsonData() {
	}

	public static String write(Object value) {
		try {
			return MAPPER.writeValueAsString(value == null ? Map.of() : value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Invalid JSON payload", ex);
		}
	}

	public static Map<String, Object> readMap(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return MAPPER.readValue(json, MAP_TYPE);
		}
		catch (JsonProcessingException ex) {
			return Map.of();
		}
	}

	public static List<String> readStringList(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return MAPPER.readValue(json, STRING_LIST_TYPE);
		}
		catch (JsonProcessingException ex) {
			return List.of();
		}
	}

	public static <T> T read(String json, TypeReference<T> type, T fallback) {
		if (json == null || json.isBlank()) {
			return fallback;
		}
		try {
			return MAPPER.readValue(json, type);
		}
		catch (JsonProcessingException ex) {
			return fallback;
		}
	}
}
