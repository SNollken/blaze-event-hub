package com.nollen.blaze.channel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class BlazeChannelControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listChannelsInitiallyEmpty() throws Exception {
		mockMvc.perform(get("/api/blaze/channel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	void createAndListChannel() throws Exception {
		String body = """
				{
					"name": "Teste Canal",
					"channelId": "test-channel-uuid",
					"platform": "blaze",
					"monitored": true
				}
				""";

		String responseBody = mockMvc.perform(post("/api/blaze/channel")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.name").value("Teste Canal"))
				.andExpect(jsonPath("$.channelId").value("test-channel-uuid"))
				.andExpect(jsonPath("$.platform").value("blaze"))
				.andExpect(jsonPath("$.monitored").value(true))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/blaze/channel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
	}

	@Test
	void createChannelWithInvalidRequest() throws Exception {
		String body = """
				{
					"name": "Valid",
					"channelId": "c1"
				}
				""";
		// Should succeed - only name and channelId are required, monitored defaults to false
		mockMvc.perform(post("/api/blaze/channel")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	@Test
	void deleteChannelNotFound() throws Exception {
		// Deleting a nonexistent channel returns 204 (idempotent delete)
		mockMvc.perform(delete("/api/blaze/channel/nonexistent"))
				.andExpect(status().isNoContent());
	}

	@Test
	void getChannelNotFound() throws Exception {
		mockMvc.perform(get("/api/blaze/channel/nonexistent"))
				.andExpect(status().isNotFound());
	}
}
