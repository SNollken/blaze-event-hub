package com.blaze.eventhub.blaze;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization of the Blaze API proxy controller: passthrough of the
 * client maps, request validation (@NotBlank params, @Valid body) and the
 * BlazeApiException status mapping from GlobalExceptionHandler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"beh.blaze.client-id=",
		"beh.blaze.client-secret=",
})
class BlazeApiControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private BlazeApiClient apiClient;

	@Test
	void profileReturnsClientMap() throws Exception {
		given(apiClient.getCurrentUserProfile()).willReturn(Map.of("id", "user-1", "username", "sofia"));

		mockMvc.perform(get("/api/blaze/users/profile"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("user-1"))
				.andExpect(jsonPath("$.username").value("sofia"));
	}

	@Test
	void channelsBySlugReturnsClientMap() throws Exception {
		given(apiClient.getChannelsBySlug("sofia")).willReturn(Map.of("slug", "sofia", "id", "ch-1"));

		mockMvc.perform(get("/api/blaze/channels").param("slug", "sofia"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("sofia"))
				.andExpect(jsonPath("$.id").value("ch-1"));
	}

	@Test
	void channelsWithoutSlugIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/blaze/channels"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message").value("Missing required parameter: slug"));
	}

	@Test
	void channelsWithBlankSlugIsValidationError() throws Exception {
		mockMvc.perform(get("/api/blaze/channels").param("slug", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void chatMessagesReturnsClientMap() throws Exception {
		given(apiClient.getChatMessages("ch-1")).willReturn(Map.of("messages", List.of()));

		mockMvc.perform(get("/api/blaze/chats/messages").param("channelId", "ch-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages").isArray());
	}

	@Test
	void chatMessagesWithoutChannelIdIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/blaze/chats/messages"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void sendMessageReturnsClientMap() throws Exception {
		given(apiClient.sendChatMessage("ch-1", "oi chat")).willReturn(Map.of("delivered", true));

		mockMvc.perform(post("/api/blaze/chats/messages")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"channelId\":\"ch-1\",\"message\":\"oi chat\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.delivered").value(true));
	}

	@Test
	void sendMessageWithBlankMessageIsValidationError() throws Exception {
		mockMvc.perform(post("/api/blaze/chats/messages")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"channelId\":\"ch-1\",\"message\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void sendMessageOver500CharsIsValidationError() throws Exception {
		String longMessage = "a".repeat(501);
		mockMvc.perform(post("/api/blaze/chats/messages")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"channelId\":\"ch-1\",\"message\":\"" + longMessage + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void blazeApiUnreachableMapsTo502WithSafeMessage() throws Exception {
		given(apiClient.getCurrentUserProfile())
				.willThrow(BlazeApiException.unreachable(new ConnectException("Connection refused")));

		mockMvc.perform(get("/api/blaze/users/profile"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("BLAZE_API_ERROR"))
				.andExpect(jsonPath("$.message").value("Blaze API is unreachable"));
	}

	@Test
	void blazeApiHttpErrorPassesStatusThrough() throws Exception {
		given(apiClient.getChannelsBySlug("missing"))
				.willThrow(new BlazeApiException(404, "Not Found", ""));

		mockMvc.perform(get("/api/blaze/channels").param("slug", "missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BLAZE_API_ERROR"));
	}
}
