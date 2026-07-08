package com.blaze.eventhub.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"eventhub.blaze.client-id=",
		"eventhub.blaze.client-secret=",
		"eventhub.security.api-key=dev-local-key",
})
class BlazeEventsExtendedControllerTests {

	@Autowired
	private MockMvc mockMvc;

	private static MockHttpServletRequestBuilder apiGet(String url) {
		return get(url).header("X-Nollen-Api-Key", "dev-local-key");
	}

	private static MockHttpServletRequestBuilder apiPost(String url) {
		return post(url).header("X-Nollen-Api-Key", "dev-local-key");
	}

	@Test
	void getEventsStatusReturnsExtendedFields() throws Exception {
		mockMvc.perform(apiGet("/api/blaze/events/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runnerRunning").value(false))
				.andExpect(jsonPath("$.clientRunning").value(false))
				.andExpect(jsonPath("$.eventCount").exists())
				.andExpect(jsonPath("$.engineAvailable").value(true));
	}

	@Test
	void startAndStopEventsEngine() throws Exception {
		mockMvc.perform(apiPost("/api/blaze/events/start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runnerRunning").value(true));

		mockMvc.perform(apiPost("/api/blaze/events/stop"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runnerRunning").value(false));
	}

	@Test
	void simulateEventReturnsLogEntry() throws Exception {
		String body = """
				{
					"eventType": "channel.chat.message",
					"message": "Teste simulado"
				}
				""";

		mockMvc.perform(apiPost("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.eventType").value("channel.chat.message"))
				.andExpect(jsonPath("$.source").value("simulate"))
				.andExpect(jsonPath("$.message").value("Teste simulado"));
	}

	@Test
	void simulateWithDefaults() throws Exception {
		mockMvc.perform(apiPost("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventType").value("channel.chat.message"))
				.andExpect(jsonPath("$.message", startsWith("Simulated event:")));
	}

	@Test
	void getEventLogReturnsEntries() throws Exception {
		// Simulate some events first
		mockMvc.perform(apiPost("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventType\": \"channel.chat.message\", \"message\": \"log test\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(apiGet("/api/blaze/events/log"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray())
				.andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)));
	}

	@Test
	void getEventLogWithFilters() throws Exception {
		mockMvc.perform(apiPost("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventType\": \"channel.follow\", \"message\": \"follow test\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(apiGet("/api/blaze/events/log")
						.param("eventType", "channel.follow"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray());
	}

	@Test
	void capabilitiesEndpointReturnsExpectedStructure() throws Exception {
		mockMvc.perform(apiGet("/api/blaze/events/capabilities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.engine").exists())
				.andExpect(jsonPath("$.eventTypes").isArray())
				.andExpect(jsonPath("$.features").exists())
				.andExpect(jsonPath("$.features.simulate").value(true))
				.andExpect(jsonPath("$.features.log").value(true))
				.andExpect(jsonPath("$.features.realConsumers").value(false))
				.andExpect(jsonPath("$.configuration").exists());
	}

	@Test
	void startStopLogsAppearInLog() throws Exception {
		mockMvc.perform(apiPost("/api/blaze/events/start"))
				.andExpect(status().isOk());

		mockMvc.perform(apiPost("/api/blaze/events/stop"))
				.andExpect(status().isOk());

		mockMvc.perform(apiGet("/api/blaze/events/log")
						.param("source", "system"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray())
				.andExpect(jsonPath("$.entries[0].source").value("system"));
	}
}
