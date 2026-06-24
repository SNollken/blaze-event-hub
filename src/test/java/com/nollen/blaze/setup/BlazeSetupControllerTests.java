package com.nollen.blaze.setup;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class BlazeSetupControllerTests {

	private static final List<String> FORBIDDEN_PUBLIC_NAMES = List.of(
			"clientSecret",
			"client_secret",
			"accessToken",
			"access_token",
			"refreshToken",
			"refresh_token",
			"codeVerifier",
			"code_verifier");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void setupEndpointReturnsSafeChecklistWithoutRealCredentials() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/blaze/setup"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appName").value("NollenBlaze"))
				.andExpect(jsonPath("$.environment").value("local"))
				.andExpect(jsonPath("$.clientIdConfigured").value(false))
				.andExpect(jsonPath("$.clientCredentialConfigured").value(false))
				.andExpect(jsonPath("$.redirectUriConfigured").value(true))
				.andExpect(jsonPath("$.redirectUri").value("http://localhost:8080/api/blaze/oauth/callback"))
				.andExpect(jsonPath("$.requestedScopes[0]").value("users.read"))
				.andExpect(jsonPath("$.requestedScopes[1]").value("offline.access"))
				.andExpect(jsonPath("$.recommendedScopes", hasSize(4)))
				.andExpect(jsonPath("$.tokenPresent").value(false))
				.andExpect(jsonPath("$.refreshCredentialPresent").value(false))
				.andExpect(jsonPath("$.oauthStartReady").value(false))
				.andExpect(jsonPath("$.checklist", hasSize(greaterThan(0))))
				.andExpect(jsonPath("$.missingItems", hasSize(greaterThan(0))))
				.andExpect(jsonPath("$.nextSteps", hasSize(greaterThan(0))))
				.andExpect(jsonPath("$.docsLinks", hasSize(4)))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("super-secret"))))
				.andReturn();

		assertNoForbiddenNames(result.getResponse().getContentAsString());
	}

	private static void assertNoForbiddenNames(String body) {
		for (String forbidden : FORBIDDEN_PUBLIC_NAMES) {
			assertThat(body).doesNotContain(forbidden);
		}
	}
}
