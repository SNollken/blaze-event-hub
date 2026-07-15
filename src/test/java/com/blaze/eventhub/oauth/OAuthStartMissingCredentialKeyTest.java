package com.blaze.eventhub.oauth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"eventhub.blaze.client-id=client-id",
		"eventhub.blaze.client-secret=client-secret",
		"eventhub.blaze.redirect-uri=https://hub.example.test/api/blaze/oauth/callback",
		"eventhub.security.credential-encryption-key="
})
@AutoConfigureMockMvc
class OAuthStartMissingCredentialKeyTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private BlazeOAuthGateway gateway;

	@Test
	void rejectsMissingCredentialEncryptionKeyBeforeCallingBlaze() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CONFIG_MISSING"))
				.andExpect(jsonPath("$.message", containsString("EVENTHUB_CREDENTIAL_ENCRYPTION_KEY")));

		verifyNoInteractions(gateway);
	}
}
