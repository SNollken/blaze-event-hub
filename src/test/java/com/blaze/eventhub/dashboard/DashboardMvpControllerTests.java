package com.blaze.eventhub.dashboard;

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
		"beh.blaze.client-id=",
		"beh.blaze.client-secret=",
})
class DashboardMvpControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void rootAndDashboardServeSpaShell() throws Exception {
		// "/" serves the SPA entry (React build when present, placeholder otherwise);
		// both carry the Blaze Event Hub title, so this assertion holds in both cases.
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Blaze Event Hub")));

		// "/dashboard" is a legacy path that now serves the same SPA shell.
		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Blaze Event Hub")));
	}

	@Test
	void spaDeepLinkRoutesServeShell() throws Exception {
		for (String path : new String[] {"/events", "/alerts", "/giveaways", "/overlays",
				// SPA aliases servidas pelos PageControllers (AlertPageController,
				// LiveEventsPageController, OverlayPageController)
				"/alerts-dashboard", "/live-events", "/overlays-dashboard"}) {
			mockMvc.perform(get(path))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("Blaze Event Hub")));
		}
	}

	@Test
	void removedBlazeChannelRoutesReturnNotFound() throws Exception {
		for (String path : new String[] {"/blaze", "/channel"}) {
			mockMvc.perform(get(path)).andExpect(status().isNotFound());
		}
	}

	@Test
	void spaRoutesServeReactBuildWhenFrontendIsBundled() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(
				new org.springframework.core.io.ClassPathResource("static/index.html").exists(),
				"frontend/dist not bundled — run `cd frontend && npm run build` and rebuild first");

		for (String path : new String[] {"/", "/events", "/alerts", "/giveaways", "/overlays"}) {
			mockMvc.perform(get(path))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("<div id=\"root\">")))
					.andExpect(content().string(containsString("/assets/")));
		}
	}

	@Test
	void mvpRoutesWorkWithoutRealCredentials() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Blaze Event Hub")));

		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Blaze Event Hub")));

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

		mockMvc.perform(get("/api/blaze/oauth/session"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(false))
				.andExpect(jsonPath("$.tokenPresent").value(false))
				.andExpect(jsonPath("$.profilePresent").value(false))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));

		mockMvc.perform(get("/api/blaze/events/status"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/overlay-profiles"))
				.andExpect(status().isOk());
	}

	@Test
	void statusExposesSafeMvpFields() throws Exception {
		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appName").value("Blaze Event Hub"))
				.andExpect(jsonPath("$.javaVersion").exists())
				.andExpect(jsonPath("$.blazeOAuthConfigured").value(false))
				.andExpect(jsonPath("$.tokenPresent").value(false))
				.andExpect(jsonPath("$.refreshCredentialPresent").value(false))
				.andExpect(jsonPath("$.oauthConnected").value(false))
				.andExpect(jsonPath("$.profilePresent").value(false))
				.andExpect(jsonPath("$.nextRecommendedAction").value("CONNECT_BLAZE"))
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
				.andExpect(jsonPath("$.layers[0].text").value("Blaze Event Hub Overlay Demo"));
	}
}
