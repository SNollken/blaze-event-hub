package com.blaze.eventhub.common;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

import com.blaze.eventhub.blaze.BlazeApiException;
import com.blaze.eventhub.common.OAuthException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private final Clock clock;

	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request);
	}

	@ExceptionHandler(ConfigurationMissingException.class)
	ResponseEntity<ApiErrorResponse> handleConfiguration(ConfigurationMissingException ex, HttpServletRequest request) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_MISSING", ex.getMessage(), request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Missing required parameter: " + ex.getParameterName(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
	}

	@ExceptionHandler(BlazeApiException.class)
	ResponseEntity<ApiErrorResponse> handleBlaze(BlazeApiException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.resolve(ex.status());
		return error(status == null ? HttpStatus.BAD_GATEWAY : status, "BLAZE_API_ERROR", ex.safeMessage(), request);
	}

	@ExceptionHandler(OAuthException.class)
	ResponseEntity<ApiErrorResponse> handleOAuth(OAuthException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
		return error(status == null ? HttpStatus.BAD_REQUEST : status, ex.getErrorCode(),
				(ex.getHttpStatus() == 401 ? ex.getMessage() + " Verifique se o Client ID, Client Secret e Redirect URI do .env pertencem ao mesmo app Blaze e se a Redirect URI esta cadastrada exatamente no console." : ex.getMessage()),
				request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(Instant.now(clock), status.value(), code, sanitize(message), request.getRequestURI()));
	}

	private static String sanitize(String message) {
		if (message == null || message.isBlank()) {
			return "Request failed";
		}
		return message
				.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
				.replaceAll("(?i)https?://[^\\s\"'}]+oauth[^\\s\"'}]+", "[REDACTED_URL]")
				.replaceAll("(?i)(clientSecret|client_secret|accessToken|access_token|refreshToken|refresh_token|codeVerifier|code_verifier|authorizationUrl|authorization_url|code|state)\\s*[=:]\\s*[^\\s,}&]+", "[REDACTED]")
				.replaceAll("(?i)\"(?:clientSecret|client_secret|accessToken|access_token|refreshToken|refresh_token|codeVerifier|code_verifier|authorizationUrl|authorization_url|code|state)\"\\s*:\\s*\"[^\"]+\"", "\"[REDACTED]\"");
	}
}
