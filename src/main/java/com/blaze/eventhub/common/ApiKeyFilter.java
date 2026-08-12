package com.blaze.eventhub.common;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;
import com.blaze.eventhub.config.ApiSecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-BEH-Api-Key";

	private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

	private static final List<String> PUBLIC_PREFIXES = List.of(
			"/api/health",
			"/api/status",
			"/api/blaze/oauth/callback",
			"/api/public/",
			"/overlay/",
			"/assets/",
			"/vite.svg");

	private final ApiSecurityProperties properties;

	public ApiKeyFilter(ApiSecurityProperties properties) {
		this.properties = properties;
		String configuredKey = properties.getApiKey();
		if (configuredKey == null || configuredKey.isBlank()) {
			// Fail-open is intentional (the key is public by design — it only blocks
			// bots/CSRF, real secrets live server-side), but an UNSET key means the
			// API is wide open with zero friction for scripts. Make it visible.
			log.warn("beh.security.api-key is blank: /api/** is OPEN. Set BEH_API_KEY in production.");
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (!path.startsWith("/api/")) {
			return true;
		}
		return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String configuredKey = properties.getApiKey();
		if (configuredKey == null || configuredKey.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}
		String requestKey = request.getHeader(HEADER_NAME);
		if (requestKey == null || requestKey.isBlank()) {
			requestKey = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
		}
		if (!MessageDigest.isEqual(
				configuredKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				(requestKey != null ? requestKey : "").getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"API key required\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private static String bearerToken(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		return authorization.substring(7).trim();
	}
}
