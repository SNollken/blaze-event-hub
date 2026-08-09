package com.nollen.blaze.blaze;

import org.springframework.web.client.RestClientResponseException;

public class BlazeApiException extends RuntimeException {

	private final int status;
	private final String responseBody;
	private final String safeMessageOverride;

	public BlazeApiException(int status, String message, String responseBody) {
		this(status, message, responseBody, null);
	}

	public BlazeApiException(int status, String message, String responseBody, String safeMessageOverride) {
		super(message);
		this.status = status;
		this.responseBody = responseBody == null ? "" : responseBody;
		this.safeMessageOverride = safeMessageOverride;
	}

	public int status() {
		return status;
	}

	public String safeMessage() {
		return safeMessageOverride != null ? safeMessageOverride : "Blaze API returned HTTP " + status;
	}

	public BlazeApiError toError() {
		return new BlazeApiError(status, safeMessage(), sanitize(responseBody));
	}

	public static BlazeApiException from(RestClientResponseException ex) {
		return new BlazeApiException(ex.getStatusCode().value(), ex.getStatusText(), ex.getResponseBodyAsString());
	}

	/**
	 * Network-level failure (connect timeout, connection refused, read timeout).
	 * There is no HTTP response, so it maps to 502 Bad Gateway instead of falling
	 * through to the generic 500 handler — callers can tell "Blaze is down" apart
	 * from an internal bug.
	 */
	public static BlazeApiException unreachable(Throwable cause) {
		return new BlazeApiException(502, "Blaze API unreachable: " + cause.getMessage(), null,
				"Blaze API is unreachable");
	}

	private static String sanitize(String body) {
		return body
				.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
				.replaceAll("(?i)(accessToken|access_token|refreshToken|refresh_token|clientSecret|client_secret)\"?\\s*[:=]\\s*\"?[^\"]+", "$1:[REDACTED]");
	}
}
