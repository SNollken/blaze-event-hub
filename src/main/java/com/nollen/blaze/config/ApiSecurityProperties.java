package com.nollen.blaze.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nollen.security")
public class ApiSecurityProperties {

	private String apiKey = "dev-local-key";

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
}
