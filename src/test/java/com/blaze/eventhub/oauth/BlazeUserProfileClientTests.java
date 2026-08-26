package com.blaze.eventhub.oauth;

import java.util.Map;

import com.blaze.eventhub.blaze.BlazeApiClient;
import com.blaze.eventhub.blaze.BlazeApiException;

import org.junit.jupiter.api.Test;

import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BlazeUserProfileClientTests {

	private final BlazeApiClient apiClient = mock(BlazeApiClient.class);

	@Test
	void delegatesProfileFetchToApiClientWithoutTransformingThePayload() {
		Map<String, Object> upstream = Map.of(
				"data", Map.of(
						"id", "user-123456789",
						"username", "sofia",
						"display_name", "Sofia Blaze",
						"avatar_url", "https://cdn.example.test/avatar.png"));
		when(apiClient.getCurrentUserProfile()).thenReturn(upstream);

		// Assignment pins the adapter's type so Spring can inject it as OAuthProfileClient.
		OAuthProfileClient client = new BlazeUserProfileClient(apiClient);

		Map<String, Object> result = client.getCurrentUserProfile();

		assertThat(result).isEqualTo(upstream);
		verify(apiClient, times(1)).getCurrentUserProfile();
		verifyNoMoreInteractions(apiClient);
	}

	@Test
	void propagatesApiExceptionsInsteadOfSwallowingThem() {
		when(apiClient.getCurrentUserProfile())
				.thenThrow(BlazeApiException.unreachable(new ResourceAccessException("connection refused")));
		OAuthProfileClient client = new BlazeUserProfileClient(apiClient);

		assertThatThrownBy(client::getCurrentUserProfile)
				.isInstanceOf(BlazeApiException.class)
				.hasMessageContaining("unreachable");
	}

	@Test
	void emptyProfilePayloadPassesThroughUntouched() {
		when(apiClient.getCurrentUserProfile()).thenReturn(Map.of());
		OAuthProfileClient client = new BlazeUserProfileClient(apiClient);

		assertThat(client.getCurrentUserProfile()).isEmpty();
	}
}
