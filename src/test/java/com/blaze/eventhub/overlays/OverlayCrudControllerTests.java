package com.blaze.eventhub.overlays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caracterização do CRUD de overlays (profiles, overlays, layers, assets) +
 * regras de segurança de upload e isolamento cross-overlay de assets.
 *
 * Nota: o relatório r51 citava "409 ao deletar profile com overlays", mas o
 * comportamento real (e o exposto pela UI) é delete em cascata — este teste
 * documenta a cascata como comportamento vigente. Também nunca existiu
 * ConflictException no pacote overlays (verificado via git log -S).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"beh.blaze.client-id=",
		"beh.blaze.client-secret=",
})
class OverlayCrudControllerTests {

	private static final byte[] PNG_BYTES = {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
			0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52 };

	private static final byte[] GIF_BYTES = { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String createProfile(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/overlay-profiles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s", "description": "perfil de teste" }
						""".formatted(name)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private JsonNode createOverlay(String profileId, String name, String type) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/overlay-profiles/{profileId}/overlays", profileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s", "type": "%s" }
						""".formatted(name, type)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	// ===== Profiles =====

	@Test
	void createProfileTrimsNameAndReturnsId() throws Exception {
		mockMvc.perform(post("/api/overlay-profiles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"  Perfil OBS  \" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Perfil OBS"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void listProfilesContainsCreatedProfile() throws Exception {
		String profileId = createProfile("Perfil na lista");
		mockMvc.perform(get("/api/overlay-profiles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '%s')]".formatted(profileId)).exists());
	}

	@Test
	void getMissingProfileReturns404WithEnvelope() throws Exception {
		mockMvc.perform(get("/api/overlay-profiles/{profileId}", "perfil-inexistente"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Overlay profile not found"))
				.andExpect(jsonPath("$.path").value("/api/overlay-profiles/perfil-inexistente"));
	}

	@Test
	void updateProfileChangesNameAndDescription() throws Exception {
		String profileId = createProfile("Antes");
		mockMvc.perform(put("/api/overlay-profiles/{profileId}", profileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"Depois\", \"description\": \"atualizado\" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Depois"))
				.andExpect(jsonPath("$.description").value("atualizado"));
	}

	@Test
	void deleteProfileReturns204ThenGetIs404() throws Exception {
		String profileId = createProfile("Para deletar");
		mockMvc.perform(delete("/api/overlay-profiles/{profileId}", profileId))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/overlay-profiles/{profileId}", profileId))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteProfileCascadesOverlaysAndPublicManifest() throws Exception {
		// Comportamento vigente (UI expõe delete com toast de sucesso): a cascata
		// remove overlays, assets e invalida o publicToken usado no OBS.
		String profileId = createProfile("Perfil com overlay");
		JsonNode overlay = createOverlay(profileId, "Overlay da cascata", "obs-scene");
		String publicToken = overlay.get("publicToken").asText();

		mockMvc.perform(delete("/api/overlay-profiles/{profileId}", profileId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/overlays/{overlayId}", overlay.get("id").asText()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/public/overlays/{publicToken}/manifest", publicToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void createProfileBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/api/overlay-profiles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"   \" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createProfileNameTooLongReturns400() throws Exception {
		String longName = "x".repeat(121);
		mockMvc.perform(post("/api/overlay-profiles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"%s\" }".formatted(longName)))
				.andExpect(status().isBadRequest());
	}

	// ===== Overlays =====

	@Test
	void createOverlayGeneratesPublicTokenAndDefaults() throws Exception {
		String profileId = createProfile("Perfil defaults");
		mockMvc.perform(post("/api/overlay-profiles/{profileId}/overlays", profileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"Meu overlay\", \"type\": \"obs-scene\" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.publicToken").isNotEmpty())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.config.canvasWidth").value(1920))
				.andExpect(jsonPath("$.config.canvasHeight").value(1080))
				.andExpect(jsonPath("$.config.transparent").value(true));
	}

	@Test
	void createOverlayOnMissingProfileReturns404() throws Exception {
		mockMvc.perform(post("/api/overlay-profiles/{profileId}/overlays", "perfil-fantasma")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"x\", \"type\": \"obs-scene\" }"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void createOverlayBlankTypeReturns400() throws Exception {
		String profileId = createProfile("Perfil validacao overlay");
		mockMvc.perform(post("/api/overlay-profiles/{profileId}/overlays", profileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"ok\", \"type\": \"\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void getMissingOverlayReturns404() throws Exception {
		mockMvc.perform(get("/api/overlays/{overlayId}", "overlay-inexistente"))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateOverlayKeepsFieldsWhenBlankAndChangesName() throws Exception {
		String profileId = createProfile("Perfil update overlay");
		JsonNode overlay = createOverlay(profileId, "Nome original", "obs-scene");
		String overlayId = overlay.get("id").asText();

		// name presente, type ausente -> type preservado
		mockMvc.perform(put("/api/overlays/{overlayId}", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"Nome novo\" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Nome novo"))
				.andExpect(jsonPath("$.type").value("obs-scene"))
				.andExpect(jsonPath("$.publicToken").value(overlay.get("publicToken").asText()));

		// name em branco -> preserva o atual
		mockMvc.perform(put("/api/overlays/{overlayId}", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"   \" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Nome novo"));
	}

	@Test
	void deleteOverlayIs204ThenSecondDeleteIs404() throws Exception {
		String profileId = createProfile("Perfil delete overlay");
		JsonNode overlay = createOverlay(profileId, "Overlay deletavel", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(delete("/api/overlays/{overlayId}", overlayId))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/overlays/{overlayId}", overlayId))
				.andExpect(status().isNotFound());
	}

	// ===== Layers =====

	@Test
	void createLayerAppliesDefaults() throws Exception {
		String profileId = createProfile("Perfil layers");
		JsonNode overlay = createOverlay(profileId, "Overlay layers", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(post("/api/overlays/{overlayId}/layers", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "type": "TEXT", "x": 10, "y": 20, "width": 300, "height": 80, "zIndex": 1, "text": "Ola" }
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visible").value(true))
				.andExpect(jsonPath("$.opacity").value(1.0))
				.andExpect(jsonPath("$.text").value("Ola"));

		mockMvc.perform(get("/api/overlays/{overlayId}/layers", overlayId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void createLayerExceedingCanvasReturns400() throws Exception {
		String profileId = createProfile("Perfil canvas");
		JsonNode overlay = createOverlay(profileId, "Overlay canvas", "obs-scene");
		String overlayId = overlay.get("id").asText();

		// x=1900 + width=100 > canvasWidth 1920
		mockMvc.perform(post("/api/overlays/{overlayId}/layers", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "type": "TEXT", "x": 1900, "y": 0, "width": 100, "height": 50, "zIndex": 0 }
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Layer bounds exceed overlay canvas"));
	}

	@Test
	void createLayerOpacityOutOfRangeReturns400() throws Exception {
		String profileId = createProfile("Perfil opacity");
		JsonNode overlay = createOverlay(profileId, "Overlay opacity", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(post("/api/overlays/{overlayId}/layers", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "type": "TEXT", "x": 0, "y": 0, "width": 100, "height": 50, "zIndex": 0, "opacity": 1.5 }
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Layer opacity must be between 0 and 1"));
	}

	@Test
	void createLayerNegativeXReturns400ByBeanValidation() throws Exception {
		String profileId = createProfile("Perfil bean validation");
		JsonNode overlay = createOverlay(profileId, "Overlay bean", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(post("/api/overlays/{overlayId}/layers", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "type": "TEXT", "x": -1, "y": 0, "width": 100, "height": 50, "zIndex": 0 }
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateLayerPartialKeepsOtherFields() throws Exception {
		String profileId = createProfile("Perfil update layer");
		JsonNode overlay = createOverlay(profileId, "Overlay update layer", "obs-scene");
		String overlayId = overlay.get("id").asText();

		String layerJson = mockMvc.perform(post("/api/overlays/{overlayId}/layers", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "type": "TEXT", "x": 5, "y": 5, "width": 200, "height": 60, "zIndex": 2, "text": "antes" }
						"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String layerId = objectMapper.readTree(layerJson).get("id").asText();

		mockMvc.perform(put("/api/overlays/{overlayId}/layers/{layerId}", overlayId, layerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"text\": \"depois\" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.text").value("depois"))
				.andExpect(jsonPath("$.x").value(5))
				.andExpect(jsonPath("$.zIndex").value(2));
	}

	@Test
	void deleteLayerIs204ThenSecondDeleteIs404() throws Exception {
		String profileId = createProfile("Perfil delete layer");
		JsonNode overlay = createOverlay(profileId, "Overlay delete layer", "obs-scene");
		String overlayId = overlay.get("id").asText();

		String layerJson = mockMvc.perform(post("/api/overlays/{overlayId}/layers", overlayId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "type": "IMAGE", "x": 0, "y": 0, "width": 100, "height": 100, "zIndex": 0 }
						"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String layerId = objectMapper.readTree(layerJson).get("id").asText();

		mockMvc.perform(delete("/api/overlays/{overlayId}/layers/{layerId}", overlayId, layerId))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/overlays/{overlayId}/layers/{layerId}", overlayId, layerId))
				.andExpect(status().isNotFound());
	}

	// ===== Assets (upload seguro + isolamento cross-overlay) =====

	@Test
	void uploadPngAssetAndReadViaPublicToken() throws Exception {
		String profileId = createProfile("Perfil assets");
		JsonNode overlay = createOverlay(profileId, "Overlay assets", "obs-scene");
		String overlayId = overlay.get("id").asText();
		String publicToken = overlay.get("publicToken").asText();

		String assetJson = mockMvc.perform(multipart("/api/overlays/{overlayId}/assets", overlayId)
				.file(new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mimeType").value("image/png"))
				.andExpect(jsonPath("$.originalFilename").value("logo.png"))
				.andReturn().getResponse().getContentAsString();
		String assetId = objectMapper.readTree(assetJson).get("id").asText();

		mockMvc.perform(get("/api/public/overlays/{publicToken}/assets/{assetId}", publicToken, assetId))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", startsWith("image/png")));
	}

	@Test
	void uploadBlockedExtensionReturns400() throws Exception {
		String profileId = createProfile("Perfil bloqueado");
		JsonNode overlay = createOverlay(profileId, "Overlay bloqueado", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(multipart("/api/overlays/{overlayId}/assets", overlayId)
				.file(new MockMultipartFile("file", "evil.html", "image/png", PNG_BYTES)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Asset file type is blocked"));
	}

	@Test
	void uploadMimeSpoofingReturns400() throws Exception {
		// declara image/png mas o conteudo nao tem magic de imagem permitida
		String profileId = createProfile("Perfil spoofing");
		JsonNode overlay = createOverlay(profileId, "Overlay spoofing", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(multipart("/api/overlays/{overlayId}/assets", overlayId)
				.file(new MockMultipartFile("file", "fake.png", "image/png",
						"<html>nao sou png</html>".getBytes())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void uploadEmptyFileReturns400() throws Exception {
		String profileId = createProfile("Perfil vazio");
		JsonNode overlay = createOverlay(profileId, "Overlay vazio", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(multipart("/api/overlays/{overlayId}/assets", overlayId)
				.file(new MockMultipartFile("file", "vazio.png", "image/png", new byte[0])))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Asset file is required"));
	}

	@Test
	void assetOfOneOverlayIsNotReadableViaAnotherOverlayPublicToken() throws Exception {
		// Isolamento (anti-IDOR): o assetId pertence ao overlay A; tentar ler via
		// publicToken do overlay B deve dar 404, nao vazar o binario.
		String profileId = createProfile("Perfil IDOR");
		JsonNode overlayA = createOverlay(profileId, "Overlay A", "obs-scene");
		JsonNode overlayB = createOverlay(profileId, "Overlay B", "obs-scene");

		String assetJson = mockMvc.perform(multipart("/api/overlays/{overlayId}/assets", overlayA.get("id").asText())
				.file(new MockMultipartFile("file", "segredo.png", "image/png", PNG_BYTES)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String assetId = objectMapper.readTree(assetJson).get("id").asText();

		mockMvc.perform(get("/api/public/overlays/{publicToken}/assets/{assetId}",
				overlayB.get("publicToken").asText(), assetId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void gifAssetIsAccepted() throws Exception {
		String profileId = createProfile("Perfil gif");
		JsonNode overlay = createOverlay(profileId, "Overlay gif", "obs-scene");
		String overlayId = overlay.get("id").asText();

		mockMvc.perform(multipart("/api/overlays/{overlayId}/assets", overlayId)
				.file(new MockMultipartFile("file", "anim.gif", "image/gif", GIF_BYTES)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mimeType").value("image/gif"));
	}
}
