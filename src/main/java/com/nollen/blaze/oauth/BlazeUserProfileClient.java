package com.nollen.blaze.oauth;

import java.util.Map;

import com.nollen.blaze.blaze.BlazeApiClient;

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
