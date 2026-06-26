package com.nollen.blaze.overlays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
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
class OverlayRuntimeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void demoOverlayRuntimeServesObsHtml() throws Exception {
		mockMvc.perform(get("/overlay/{publicToken}", OverlayService.DEMO_PUBLIC_TOKEN))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("overlay runtime")))
				.andExpect(content().string(containsString("/api/public/overlays/{publicToken}/manifest")))
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("client_secret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("access_token"))))
				.andExpect(content().string(not(containsString("refreshToken"))))
				.andExpect(content().string(not(containsString("refresh_token"))));
	}

	@Test
	void demoManifestIsPublicAndDoesNotExposeSecrets() throws Exception {
		mockMvc.perform(get("/api/public/overlays/{publicToken}/manifest", OverlayService.DEMO_PUBLIC_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.name").value("Overlay de Teste"))
				.andExpect(jsonPath("$.publicToken").value(OverlayService.DEMO_PUBLIC_TOKEN))
				.andExpect(jsonPath("$.config.transparent").value(true))
				.andExpect(jsonPath("$.layers[0].text").value("NollenBlaze Overlay Demo"))
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("client_secret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("access_token"))))
				.andExpect(content().string(not(containsString("refreshToken"))))
				.andExpect(content().string(not(containsString("refresh_token"))));
	}

	@Test
	void unknownTokenStillServesRuntimeShellAndManifestStaysControlled() throws Exception {
		mockMvc.perform(get("/overlay/token-inexistente"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("overlay runtime")));

		mockMvc.perform(get("/api/public/overlays/token-inexistente/manifest"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));
	}

	@Test
	void runtimeAssetsAreAvailableWithoutSensitiveFields() throws Exception {
		mockMvc.perform(get("/overlay-runtime.css"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("background: transparent")));

		mockMvc.perform(get("/overlay-runtime.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Overlay nao encontrada.")))
				.andExpect(content().string(not(containsString("clientSecret"))))
				.andExpect(content().string(not(containsString("accessToken"))))
				.andExpect(content().string(not(containsString("refreshToken"))));
	}

	@Test
	void manifestIncludesAssetUrls() throws Exception {
		mockMvc.perform(get("/api/public/overlays/{publicToken}/manifest", OverlayService.DEMO_PUBLIC_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assets").isArray())
				.andExpect(content().string(not(containsString("clientSecret"))));
	}

	@Test
	void unknownTokenAssetReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/public/overlays/token-inexistente/assets/fake-id"))
				.andExpect(status().isNotFound());
	}

	@Test
	void runtimeJsSupportsDebugMode() throws Exception {
		mockMvc.perform(get("/overlay-runtime.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("debugMode")))
				.andExpect(content().string(containsString("debug-info")));
	}

	@Test
	void overlayRuntimeHasObsCompatCss() throws Exception {
		mockMvc.perform(get("/overlay-runtime.css"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("pointer-events: none")))
				.andExpect(content().string(containsString("overflow: hidden")));
	}

	@Test
	void requiredDashboardSmokeRoutesStillReturnOk() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/blaze/setup"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/blaze/events/status"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/overlay-profiles"))
				.andExpect(status().isOk());
	}
}
