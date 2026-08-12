package com.blaze.eventhub.oauth;

import java.util.Map;

import com.blaze.eventhub.blaze.BlazeApiClient;

import org.springframework.stereotype.Component;

@Component
public class BlazeUserProfileClient implements OAuthProfileClient {

	private final BlazeApiClient apiClient;

	public BlazeUserProfileClient(BlazeApiClient apiClient) {
		this.apiClient = apiClient;
	}

	@Override
	public Map<String, Object> getCurrentUserProfile() {
		return apiClient.getCurrentUserProfile();
	}
}
