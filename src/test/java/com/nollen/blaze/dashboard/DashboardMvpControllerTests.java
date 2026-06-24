package com.nollen.blaze.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class DashboardMvpControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void rootAndDashboardServeProvisionalPanel() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("NollenBlaze")))
				.andExpect(content().string(containsString("Painel MVP 2")))
				.andExpect(content().string(containsString("Tela provisoria")))
				.andExpect(content().string(containsString("Configuracao Blaze")))
				.andExpect(content().string(containsString("Copiar Redirect URI")))
				.andExpect(content().string(containsString("Copiar scopes")));

		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("NollenBlaze")))
				.andExpect(content().string(containsString("OpenDesign/opencode")))
				.andExpect(content().string(containsString("Iniciar OAuth")));
	}

	@Test
	void mvpRoutesWorkWithoutRealCredentials() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("NollenBlaze")));

		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Tela provisoria")));

		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));

		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));

		mockMvc.perform(get("/api/blaze/setup"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("client_secret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("access_token"))))
				.andExpect(content().string(not(containsString("refreshToken"))))
				.andExpect(content().string(not(containsString("refresh_token"))));

		mockMvc.perform(get("/api/blaze/events/status"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/overlay-profiles"))
				.andExpect(status().isOk());
	}

	@Test
	void statusExposesSafeMvpFields() throws Exception {
		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appName").value("NollenBlaze"))
				.andExpect(jsonPath("$.javaVersion").exists())
				.andExpect(jsonPath("$.blazeOAuthConfigured").value(false))
				.andExpect(jsonPath("$.tokenPresent").value(false))
				.andExpect(jsonPath("$.refreshCredentialPresent").value(false))
				.andExpect(jsonPath("$.eventsRunning").value(false))
				.andExpect(jsonPath("$.sessionIdPresent").value(false))
				.andExpect(jsonPath("$.activeProfilesCount").value(1))
				.andExpect(jsonPath("$.overlaysCount").value(1))
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));
	}

	@Test
	void eventsStatusAndSyncFailSafelyWithoutSessionOrChannel() throws Exception {
		mockMvc.perform(get("/api/blaze/events/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runnerRunning").value(false))
				.andExpect(jsonPath("$.clientRunning").value(false));

		mockMvc.perform(post("/api/blaze/events/subscriptions/sync"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CONFIG_MISSING"))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));
	}

	@Test
	void oauthStartWithoutConfigFailsSafely() throws Exception {
		mockMvc.perform(post("/api/blaze/oauth/start"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CONFIG_MISSING"))
				.andExpect(jsonPath("$.message").value("Blaze OAuth is not configured"))
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));
	}

	@Test
	void overlayProfilesAndDemoManifestAreAvailable() throws Exception {
		MvcResult profilesResult = mockMvc.perform(get("/api/overlay-profiles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Demo"))
				.andReturn();

		JsonNode profiles = objectMapper.readTree(profilesResult.getResponse().getContentAsString());
		String profileId = profiles.get(0).get("id").asText();

		MvcResult overlaysResult = mockMvc.perform(get("/api/overlay-profiles/{profileId}/overlays", profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Overlay de Teste"))
				.andReturn();

		JsonNode overlays = objectMapper.readTree(overlaysResult.getResponse().getContentAsString());
		String publicToken = overlays.get(0).get("publicToken").asText();

		mockMvc.perform(get("/api/public/overlays/{publicToken}/manifest", publicToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Overlay de Teste"))
				.andExpect(jsonPath("$.layers[0].text").value("NollenBlaze"));
	}
}
