package com.blaze.eventhub.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"nollen.blaze.client-secret=secret-that-must-not-leak",
		"nollen.blaze.client-id=test-client"
})
@AutoConfigureMockMvc
class HealthStatusControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthReturnsOk() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"))
				.andExpect(jsonPath("$.app").value("NollenBlaze"));
	}

	@Test
	void statusDoesNotLeakSecret() throws Exception {
		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appName").value("NollenBlaze"))
				.andExpect(jsonPath("$.javaVersion").exists())
				.andExpect(content().string(not(containsString("secret-that-must-not-leak"))))
				.andExpect(content().string(not(containsString("access-token"))))
				.andExpect(content().string(not(containsString("refresh-token"))));
	}
}
