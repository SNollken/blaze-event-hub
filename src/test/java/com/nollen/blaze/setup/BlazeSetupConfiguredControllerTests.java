package com.nollen.blaze.setup;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"nollen.blaze.client-id=abc123456789xyz",
		"nollen.blaze.client-secret=placeholder-backend-credential-123",
		"nollen.blaze.redirect-uri=http://localhost:8080/api/blaze/oauth/callback",
		"nollen.blaze.scopes=users.read,offline.access",
		"nollen.blaze.monitored-channel-id=channel-1234567890"
})
@AutoConfigureMockMvc
class BlazeSetupConfiguredControllerTests {

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
	void configuredSetupMasksIdentifiersAndNeverReturnsSecretValues() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/blaze/setup"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientIdConfigured").value(true))
				.andExpect(jsonPath("$.clientIdMasked").value("abc1...9xyz"))
				.andExpect(jsonPath("$.clientCredentialConfigured").value(true))
				.andExpect(jsonPath("$.monitoredChannelConfigured").value(true))
				.andExpect(jsonPath("$.monitoredChannel").value("chan...7890"))
				.andExpect(jsonPath("$.oauthStartReady").value(true))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body)
				.doesNotContain("abc123456789xyz")
				.doesNotContain("placeholder-backend-credential-123")
				.doesNotContain("channel-1234567890");
		for (String forbidden : FORBIDDEN_PUBLIC_NAMES) {
			assertThat(body).doesNotContain(forbidden);
		}
	}
}
