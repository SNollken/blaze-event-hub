package com.blaze.eventhub.blaze;

import org.springframework.web.client.RestClientResponseException;

public class BlazeApiException extends RuntimeException {

	private final int status;
	private final String responseBody;

	public BlazeApiException(int status, String message, String responseBody) {
		super(message);
		this.status = status;
		this.responseBody = responseBody == null ? "" : responseBody;
	}

	public int status() {
		return status;
	}

	public String safeMessage() {
		return "Blaze API returned HTTP " + status;
	}

	public BlazeApiError toError() {
		return new BlazeApiError(status, safeMessage(), sanitize(responseBody));
	}

	public static BlazeApiException from(RestClientResponseException ex) {
		return new BlazeApiException(ex.getRawStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
	}

	private static String sanitize(String body) {
		return body
				.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
				.replaceAll("(?i)(accessToken|access_token|refreshToken|refresh_token|clientSecret|client_secret)\"?\\s*[:=]\\s*\"?[^\"]+", "$1:[REDACTED]");
	}
}
