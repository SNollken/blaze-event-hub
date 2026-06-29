package com.nollen.blaze.common;

import java.io.IOException;
import java.util.List;

import com.nollen.blaze.config.ApiSecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Nollen-Api-Key";

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
		if (!configuredKey.equals(requestKey)) {
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
