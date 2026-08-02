package com.nollen.blaze.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: verifica que ApiKeyFilter esta registrado e ativo no contexto Spring,
 * exigindo X-Nollen-Api-Key em endpoints administrativos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.security.api-key=test-integration-key",
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class ApiKeyFilterIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicApiDoesNotRequireKey() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void adminApiWithoutKeyReturns401() throws Exception {
		mockMvc.perform(get("/api/alerts/stats"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void adminApiWithValidKeyReturns200() throws Exception {
		mockMvc.perform(get("/api/alerts/stats")
				.header(ApiKeyFilter.HEADER_NAME, "test-integration-key"))
				.andExpect(status().isOk());
	}

	@Test
	void adminApiWithWrongKeyReturns401() throws Exception {
		mockMvc.perform(get("/api/alerts/stats")
				.header(ApiKeyFilter.HEADER_NAME, "wrong-key"))
				.andExpect(status().isUnauthorized());
	}
}
