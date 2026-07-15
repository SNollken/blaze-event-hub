package com.blaze.eventhub.blaze;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.oauth.InMemoryTokenStore;
import com.blaze.eventhub.oauth.TokenSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class BlazeApiClientTests {

	private BlazeProperties properties;
	private InMemoryTokenStore tokenStore;
	private MockRestServiceServer server;
	private BlazeApiClient client;

	@BeforeEach
	void setUp() {
		properties = new BlazeProperties();
		properties.setClientId("client-123");
		properties.setApiBaseUrl("https://api.blaze.stream");
		tokenStore = new InMemoryTokenStore();
		tokenStore.save(new TokenSnapshot("user", "user-1", "Bearer", "access-token-secret", "refresh-token-secret",
				Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now()));
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new BlazeApiClient(properties, tokenStore, new BlazeApiHeaders(properties), builder);
	}

	@Test
	void sendsRequiredHeaders() {
		server.expect(once(), requestTo("https://api.blaze.stream/v1/users/profile"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("client-id", "client-123"))
				.andExpect(header("Authorization", "Bearer access-token-secret"))
				.andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

		Map<String, Object> response = client.getCurrentUserProfile();

		assertThat(response).containsEntry("success", true);
		server.verify();
	}

	@Test
	void mapsUnauthorizedWithoutLeakingToken() {
		server.expect(once(), requestTo("https://api.blaze.stream/v1/users/profile"))
				.andRespond(withStatus(UNAUTHORIZED).body("{\"accessToken\":\"access-token-secret\"}"));

		assertThatThrownBy(() -> client.getCurrentUserProfile())
				.isInstanceOf(BlazeApiException.class)
				.extracting(ex -> ((BlazeApiException) ex).safeMessage())
				.asString()
				.doesNotContain("access-token-secret")
				.contains("401");
	}

	@Test
	void mapsForbiddenAndRateLimit() {
		server.expect(once(), requestTo("https://api.blaze.stream/v1/users/profile"))
				.andRespond(withStatus(FORBIDDEN));
		assertThatThrownBy(() -> client.getCurrentUserProfile())
				.isInstanceOf(BlazeApiException.class)
				.extracting(ex -> ((BlazeApiException) ex).status())
				.isEqualTo(403);

		server.reset();
		server.expect(once(), requestTo("https://api.blaze.stream/v1/users/profile"))
				.andRespond(withStatus(TOO_MANY_REQUESTS));
		assertThatThrownBy(() -> client.getCurrentUserProfile())
				.isInstanceOf(BlazeApiException.class)
				.extracting(ex -> ((BlazeApiException) ex).status())
				.isEqualTo(429);
	}

	@Test
	void getsConfiguredNumberOfChatMessagesWithAnExplicitBackgroundToken() {
		properties.setChatMessageLimit(75);
		TokenSnapshot backgroundToken = new TokenSnapshot(
				"user", "creator-1", "Bearer", "background-access-token", "refresh",
				Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now());
		server.expect(once(), requestTo(
				"https://api.blaze.stream/v1/chats/messages?channelId=channel-1&limit=75"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer background-access-token"))
				.andRespond(withSuccess("{\"data\":{\"channelId\":\"channel-1\",\"messages\":[]}}",
						MediaType.APPLICATION_JSON));

		Map<String, Object> response = client.getChatMessages("channel-1", backgroundToken);

		assertThat(response).containsKey("data");
		server.verify();
	}

	@Test
	void resolvesChannelSlugWithTheExplicitPersistentCredential() {
		TokenSnapshot persistentToken = new TokenSnapshot(
				"user", "creator-1", "Bearer", "persistent-access-token", "refresh",
				Instant.now().plusSeconds(3600), List.of("users.read", "offline.access"), Instant.now());
		server.expect(once(), requestTo(
				"https://api.blaze.stream/v1/channels?slug%5B%5D=snollken"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("client-id", "client-123"))
				.andExpect(header("Authorization", "Bearer persistent-access-token"))
				.andRespond(withSuccess(
						"{\"data\":{\"rows\":[{\"id\":\"channel-42\",\"slug\":\"snollken\"}]}}",
						MediaType.APPLICATION_JSON));

		Map<String, Object> response = client.getChannelsBySlug("snollken", persistentToken);

		assertThat(response).containsKey("data");
		server.verify();
	}

	@Test
	void sendsOpaqueCursorWhenReadingTheNextChatPage() {
		TokenSnapshot backgroundToken = new TokenSnapshot(
				"user", "creator-1", "Bearer", "background-access-token", "refresh",
				Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now());
		server.expect(once(), requestTo(
				"https://api.blaze.stream/v1/chats/messages?channelId=channel-1&cursor=next%2Fpage%2B1&limit=100"))
				.andRespond(withSuccess("{\"data\":{\"messages\":[],\"pagination\":{\"cursor\":null}}}",
						MediaType.APPLICATION_JSON));

		client.getChatMessages("channel-1", "next/page+1", backgroundToken);

		server.verify();
	}
}
