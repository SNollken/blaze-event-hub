package com.blaze.eventhub.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eventhub.blaze")
public class BlazeProperties {

	private String clientId = "";
	private String clientSecret = "";
	private String redirectUri = "http://localhost:8080/api/blaze/oauth/callback";
	private String authBaseUrl = "https://blaze.stream";
	private String apiBaseUrl = "https://api.blaze.stream";
	private String socketUrl = "https://blaze.stream";
	private String socketPath = "/ws";
	private List<String> scopes = new ArrayList<>(List.of("users.read", "offline.access"));
	private String monitoredChannelId = "";

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clean(clientId);
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clean(clientSecret);
	}

	public String getRedirectUri() {
		return redirectUri;
	}

	public void setRedirectUri(String redirectUri) {
		this.redirectUri = clean(redirectUri);
	}

	public String getAuthBaseUrl() {
		return authBaseUrl;
	}

	public void setAuthBaseUrl(String authBaseUrl) {
		this.authBaseUrl = trimTrailingSlash(clean(authBaseUrl));
	}

	public String getApiBaseUrl() {
		return apiBaseUrl;
	}

	public void setApiBaseUrl(String apiBaseUrl) {
		this.apiBaseUrl = trimTrailingSlash(clean(apiBaseUrl));
	}

	public String getSocketUrl() {
		return socketUrl;
	}

	public void setSocketUrl(String socketUrl) {
		this.socketUrl = trimTrailingSlash(clean(socketUrl));
	}

	public String getSocketPath() {
		return socketPath;
	}

	public void setSocketPath(String socketPath) {
		this.socketPath = clean(socketPath);
	}

	public List<String> getScopes() {
		return List.copyOf(scopes);
	}

	public void setScopes(List<String> scopes) {
		this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
	}

	public String getMonitoredChannelId() {
		return monitoredChannelId;
	}

	public void setMonitoredChannelId(String monitoredChannelId) {
		this.monitoredChannelId = clean(monitoredChannelId);
	}

	public boolean isOAuthConfigured() {
		return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
	}

	public boolean isApiConfigured() {
		return hasText(clientId) && hasText(apiBaseUrl);
	}

	public boolean isSocketConfigured() {
		return hasText(socketUrl) && hasText(socketPath);
	}

	public boolean isMonitoredChannelConfigured() {
		return hasText(monitoredChannelId);
	}

	@Override
	public String toString() {
		return "BlazeProperties{" +
				"clientId='" + mask(clientId) + '\'' +
				", clientSecret='[REDACTED]'" +
				", redirectUri='" + redirectUri + '\'' +
				", authBaseUrl='" + authBaseUrl + '\'' +
				", apiBaseUrl='" + apiBaseUrl + '\'' +
				", socketUrl='" + socketUrl + '\'' +
				", socketPath='" + socketPath + '\'' +
				", scopes=" + scopes +
				", monitoredChannelId='" + mask(monitoredChannelId) + '\'' +
				'}';
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.length() <= 1) {
			return value;
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String mask(String value) {
		if (!hasText(value)) {
			return "";
		}
		if (value.length() <= 6) {
			return "***";
		}
		return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
	}
}
