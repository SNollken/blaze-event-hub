package com.blaze.eventhub.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eventhub.blaze")
public class BlazeProperties {

	private String clientId = "";
	private String clientSecret = "";
	private String redirectUri = "http://localhost:9090/api/blaze/oauth/callback";
	private String authBaseUrl = "https://blaze.stream";
	private String apiBaseUrl = "https://api.blaze.stream";
	private List<String> scopes = new ArrayList<>(List.of("users.read", "offline.access"));
	@Min(10)
	@Max(100)
	private int chatMessageLimit = 100;
	@Min(1)
	@Max(100)
	private int chatMaxPagesPerPoll = 20;
	@Min(60_000)
	private long chatHistoryCoverageMaxAgeMs = 25_200_000;

	// Socket.IO configuration
	@Min(1)
	@Max(60)
	private int socketReconnectIntervalSec = 10;
	@Min(1)
	@Max(300)
	private int socketReconnectAttempts = 5;
	private String socketUrl = "https://blaze.stream";

	public String getSocketUrl() {
		return socketUrl;
	}

	public void setSocketUrl(String socketUrl) {
		this.socketUrl = clean(socketUrl);
	}

	public int getSocketReconnectIntervalSec() {
		return socketReconnectIntervalSec;
	}

	public void setSocketReconnectIntervalSec(int socketReconnectIntervalSec) {
		this.socketReconnectIntervalSec = socketReconnectIntervalSec;
	}

	public int getSocketReconnectAttempts() {
		return socketReconnectAttempts;
	}

	public void setSocketReconnectAttempts(int socketReconnectAttempts) {
		this.socketReconnectAttempts = socketReconnectAttempts;
	}

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

	public List<String> getScopes() {
		return List.copyOf(scopes);
	}

	public void setScopes(List<String> scopes) {
		this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
	}

	public int getChatMessageLimit() {
		return chatMessageLimit;
	}

	public void setChatMessageLimit(int chatMessageLimit) {
		this.chatMessageLimit = chatMessageLimit;
	}

	public int getChatMaxPagesPerPoll() {
		return chatMaxPagesPerPoll;
	}

	public void setChatMaxPagesPerPoll(int chatMaxPagesPerPoll) {
		this.chatMaxPagesPerPoll = chatMaxPagesPerPoll;
	}

	public long getChatHistoryCoverageMaxAgeMs() {
		return chatHistoryCoverageMaxAgeMs;
	}

	public void setChatHistoryCoverageMaxAgeMs(long chatHistoryCoverageMaxAgeMs) {
		this.chatHistoryCoverageMaxAgeMs = chatHistoryCoverageMaxAgeMs;
	}

	public boolean isOAuthConfigured() {
		return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
	}

	public boolean isApiConfigured() {
		return hasText(clientId) && hasText(apiBaseUrl);
	}

	@Override
	public String toString() {
		return "BlazeProperties{"
				+ "clientId='" + mask(clientId) + '\''
				+ ", clientSecret='[REDACTED]'"
				+ ", redirectUri='" + redirectUri + '\''
				+ ", authBaseUrl='" + authBaseUrl + '\''
				+ ", apiBaseUrl='" + apiBaseUrl + '\''
				+ ", scopes=" + scopes
				+ ", chatMessageLimit=" + chatMessageLimit
				+ ", chatMaxPagesPerPoll=" + chatMaxPagesPerPoll
				+ ", chatHistoryCoverageMaxAgeMs=" + chatHistoryCoverageMaxAgeMs
				+ ", socketUrl='" + socketUrl + '\''
				+ ", socketReconnectIntervalSec=" + socketReconnectIntervalSec
				+ ", socketReconnectAttempts=" + socketReconnectAttempts
				+ '}';
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