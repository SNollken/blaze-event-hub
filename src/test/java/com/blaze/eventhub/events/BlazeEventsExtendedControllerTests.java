package com.blaze.eventhub.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class BlazeEventsExtendedControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getEventsStatusReturnsExtendedFields() throws Exception {
		mockMvc.perform(get("/api/blaze/events/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runnerRunning").value(false))
				.andExpect(jsonPath("$.clientRunning").value(false))
				.andExpect(jsonPath("$.eventCount").exists())
				.andExpect(jsonPath("$.engineAvailable").value(true));
	}

	@Test
	void startAndStopEventsEngine() throws Exception {
		mockMvc.perform(post("/api/blaze/events/start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runnerRunning").value(true));

		mockMvc.perform(post("/api/blaze/events/stop"))
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

		mockMvc.perform(post("/api/blaze/events/simulate")
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
		mockMvc.perform(post("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventType").value("channel.chat.message"))
				.andExpect(jsonPath("$.message", startsWith("Simulated event:")));
	}

	@Test
	void getEventLogReturnsEntries() throws Exception {
		// Simulate some events first
		mockMvc.perform(post("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventType\": \"channel.chat.message\", \"message\": \"log test\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/events/log"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray())
				.andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)));
	}

	@Test
	void getEventLogWithFilters() throws Exception {
		mockMvc.perform(post("/api/blaze/events/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventType\": \"channel.follow\", \"message\": \"follow test\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/events/log")
						.param("eventType", "channel.follow"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray());
	}

	@Test
	void capabilitiesEndpointReturnsExpectedStructure() throws Exception {
		mockMvc.perform(get("/api/blaze/events/capabilities"))
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
		mockMvc.perform(post("/api/blaze/events/start"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/blaze/events/stop"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/events/log")
						.param("source", "system"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray())
				.andExpect(jsonPath("$.entries[0].source").value("system"));
	}
}
