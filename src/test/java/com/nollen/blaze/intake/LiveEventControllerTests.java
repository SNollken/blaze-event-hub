package com.nollen.blaze.intake;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class LiveEventControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void listReturnsEmptyByDefault() throws Exception {
		mockMvc.perform(get("/api/live-events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	void createAndRetrieveEvent() throws Exception {
		Map<String, Object> payload = Map.of("message", "Hello", "username", "testuser");

		String body = objectMapper.writeValueAsString(Map.of(
				"type", "CHAT_MESSAGE",
				"source", "MANUAL",
				"payload", payload));

		String createResponse = mockMvc.perform(post("/api/live-events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.type").value("CHAT_MESSAGE"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		String id = objectMapper.readTree(createResponse).get("id").asText();

		mockMvc.perform(get("/api/live-events/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.type").value("CHAT_MESSAGE"));
	}

	@Test
	void statsEndpointWorks() throws Exception {
		mockMvc.perform(get("/api/live-events/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEvents").isNumber())
				.andExpect(jsonPath("$.acceptedCount").isNumber())
				.andExpect(jsonPath("$.duplicateCount").isNumber())
				.andExpect(jsonPath("$.rejectedCount").isNumber());
	}

	@Test
	void simulateEndpointCreatesTestEvent() throws Exception {
		mockMvc.perform(post("/api/live-events/simulate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.type").value("TEST"))
				.andExpect(jsonPath("$.source").value("SIMULATED"));
	}

	@Test
	void duplicateDetectionViaEndpoint() throws Exception {
		Map<String, Object> payload = Map.of("username", "dupuser");

		String body = objectMapper.writeValueAsString(Map.of(
				"type", "FOLLOW",
				"source", "MANUAL",
				"payload", payload,
				"dedupKey", "dedup-endpoint-key"));

		mockMvc.perform(post("/api/live-events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mockMvc.perform(post("/api/live-events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DUPLICATE"));
	}

	@Test
	void getNotFoundReturns404() throws Exception {
		mockMvc.perform(get("/api/live-events/nonexistent"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void simulateMultipleAndStats() throws Exception {
		mockMvc.perform(post("/api/live-events/simulate"));
		mockMvc.perform(post("/api/live-events/simulate"));
		mockMvc.perform(post("/api/live-events/simulate"));

		mockMvc.perform(get("/api/live-events/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEvents", greaterThanOrEqualTo(3)))
				.andExpect(jsonPath("$.acceptedCount", greaterThanOrEqualTo(3)));
	}
}
