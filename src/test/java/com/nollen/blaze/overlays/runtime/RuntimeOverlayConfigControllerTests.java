package com.nollen.blaze.overlays.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class RuntimeOverlayConfigControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listOverlaysReturnsSeededConfigs() throws Exception {
		mockMvc.perform(get("/api/overlay-runtimes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(greaterThan(0))));
	}

	@Test
	void getOverlayByIdReturnsConfig() throws Exception {
		String response = mockMvc.perform(get("/api/overlay-runtimes"))
				.andReturn().getResponse().getContentAsString();
		String overlayId = extractId(response);
		mockMvc.perform(get("/api/overlay-runtimes/{id}", overlayId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.type", notNullValue()));
	}

	@Test
	void createOverlayConfig() throws Exception {
		String body = """
				{
					"type": "ALERT",
					"name": "Test Alert Overlay",
					"enabled": true,
					"refreshIntervalMs": 5000,
					"customCss": ".test { color: red; }",
					"positionX": 100,
					"positionY": 50,
					"positionWidth": 500,
					"positionHeight": 300,
					"opacity": 0.9
				}
				""";
		mockMvc.perform(post("/api/overlay-runtimes")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.name").value("Test Alert Overlay"))
				.andExpect(jsonPath("$.type").value("ALERT"))
				.andExpect(jsonPath("$.refreshIntervalMs").value(5000))
				.andExpect(jsonPath("$.positionX").value(100))
				.andExpect(jsonPath("$.opacity").value(0.9));
	}

	@Test
	void updateOverlayConfig() throws Exception {
		String created = mockMvc.perform(post("/api/overlay-runtimes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "GIVEAWAY", "name": "Update Me"}
								"""))
				.andReturn().getResponse().getContentAsString();
		String overlayId = extractId(created);

		mockMvc.perform(put("/api/overlay-runtimes/{id}", overlayId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Updated Giveaway", "enabled": false, "refreshIntervalMs": 10000}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Updated Giveaway"))
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.refreshIntervalMs").value(10000));
	}

	@Test
	void deleteOverlayConfig() throws Exception {
		String created = mockMvc.perform(post("/api/overlay-runtimes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "EVENTS", "name": "Delete Me"}
								"""))
				.andReturn().getResponse().getContentAsString();
		String overlayId = extractId(created);

		mockMvc.perform(delete("/api/overlay-runtimes/{id}", overlayId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/overlay-runtimes/{id}", overlayId))
				.andExpect(status().isNotFound());
	}

	@Test
	void getNonexistentOverlayReturns404() throws Exception {
		mockMvc.perform(get("/api/overlay-runtimes/nonexistent-id"))
				.andExpect(status().isNotFound());
	}

	@Test
	void alertOverlayHtmlEndpoint() throws Exception {
		mockMvc.perform(get("/overlay/alerts"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("overlay-container")))
				.andExpect(content().string(containsString("alert")));
	}

	@Test
	void giveawayOverlayHtmlEndpoint() throws Exception {
		mockMvc.perform(get("/overlay/giveaways"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("overlay-container")))
				.andExpect(content().string(containsString("giveaway")));
	}

	@Test
	void eventsOverlayHtmlEndpoint() throws Exception {
		mockMvc.perform(get("/overlay/events"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("overlay-container")))
				.andExpect(content().string(containsString("events")));
	}

	@Test
	void overlaysDashboardEndpoint() throws Exception {
		mockMvc.perform(get("/overlays-dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("Overlays Dashboard")));
	}

	private String extractId(String jsonResponse) {
		int idx = jsonResponse.indexOf("\"id\":\"");
		if (idx < 0) throw new RuntimeException("Could not find id in: " + jsonResponse);
		int start = idx + 6;
		int end = jsonResponse.indexOf("\"", start);
		return jsonResponse.substring(start, end);
	}
}
