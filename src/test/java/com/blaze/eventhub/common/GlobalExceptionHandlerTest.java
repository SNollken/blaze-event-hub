package com.blaze.eventhub.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
			Clock.fixed(Instant.parse("2026-08-02T12:00:00Z"), ZoneOffset.UTC));

	private HttpServletRequest mockRequest(String path) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn(path);
		return request;
	}

	@Test
	void handleNotFoundReturns404() {
		var response = handler.handleNotFound(new NotFoundException("Item not found"), mockRequest("/api/test"));
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
		assertThat(response.getBody().message()).isEqualTo("Item not found");
		assertThat(response.getBody().path()).isEqualTo("/api/test");
	}

	@Test
	void handleConflictReturns409() {
		var response = handler.handleConflict(new ConflictException("Can't do this"), mockRequest("/api/test"));
		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody().code()).isEqualTo("CONFLICT");
	}

	@Test
	void handleBadRequestReturns400() {
		var response = handler.handleBadRequest(new IllegalArgumentException("Bad input"), mockRequest("/api/test"));
		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
		assertThat(response.getBody().message()).isEqualTo("Bad input");
	}

	@Test
	void handleConfigurationMissingReturns503() {
		var response = handler.handleConfiguration(new ConfigurationMissingException("Missing config"), mockRequest("/api/test"));
		assertThat(response.getStatusCode().value()).isEqualTo(503);
		assertThat(response.getBody().code()).isEqualTo("CONFIG_MISSING");
	}

	@Test
	void handleUnexpectedReturns500WithSafeMessage() {
		var response = handler.handleUnexpected(new RuntimeException("DB exploded"), mockRequest("/api/test"));
		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
		assertThat(response.getBody().message()).doesNotContain("DB exploded");
	}

	@Test
	void oAuth401RedactsSecretsAndAddsHint() {
		String secretToken = "super-secret-token-xyz";
		String refreshToken = "refresh-abc";
		OAuthException ex = new OAuthException(401, "OAUTH_ERROR",
				"Invalid: Bearer " + secretToken + " refresh_token=" + refreshToken);
		var response = handler.handleOAuth(ex, mockRequest("/api/blaze/oauth/callback"));
		assertThat(response.getStatusCode().value()).isEqualTo(401);
		String message = response.getBody().message();
		assertThat(message).contains("[REDACTED]");
		assertThat(message).contains("Verify that the Client ID");
	assertThat(message).doesNotContain("Verifique");
		assertThat(message).doesNotContain(secretToken);
		assertThat(message).doesNotContain(refreshToken);
	}

	@Test
	void blankMessageReturnsSafeDefault() {
		var response = handler.handleNotFound(new NotFoundException(null), mockRequest("/api/test"));
		assertThat(response.getBody().message()).isEqualTo("Request failed");
	}

	@Test
	void handleNoResourceReturns404() {
		var response = handler.handleNoResource(
				new NoResourceFoundException(HttpMethod.GET, "/overlay/abc/def"), mockRequest("/overlay/abc/def"));
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
		assertThat(response.getBody().message()).isEqualTo("Resource not found");
		assertThat(response.getBody().path()).isEqualTo("/overlay/abc/def");
	}
}
