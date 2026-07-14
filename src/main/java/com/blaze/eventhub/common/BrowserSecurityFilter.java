package com.blaze.eventhub.common;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import com.blaze.eventhub.config.ApiSecurityProperties;
import com.blaze.eventhub.oauth.TokenStore;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BrowserSecurityFilter extends OncePerRequestFilter {

	private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; "
			+ "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; "
			+ "script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
			+ "img-src 'self' data: https:; font-src 'self' data: https://fonts.gstatic.com; "
			+ "connect-src 'self'";

	private final ApiSecurityProperties properties;
	private final TokenStore tokenStore;

	public BrowserSecurityFilter(ApiSecurityProperties properties, TokenStore tokenStore) {
		this.properties = properties;
		this.tokenStore = tokenStore;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
		response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
		response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
		if (request.isSecure()) {
			response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
		}
		if (requiresCsrfValidation(request) && authenticatedSession()
				&& !ApiKeyFilter.hasValidInternalKey(request, properties)
				&& !hasSameOriginProof(request)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write(
					"{\"code\":\"CSRF_VALIDATION_FAILED\",\"message\":\"Origem da requisicao nao autorizada\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean authenticatedSession() {
		try {
			return tokenStore.current().filter(token -> !token.accessTokenBlank()).isPresent();
		}
		catch (RuntimeException noActiveSession) {
			return false;
		}
	}

	private static boolean requiresCsrfValidation(HttpServletRequest request) {
		if (!request.getRequestURI().startsWith("/api/")) {
			return false;
		}
		String method = request.getMethod();
		return HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method)
				|| HttpMethod.PATCH.matches(method) || HttpMethod.DELETE.matches(method);
	}

	private static boolean hasSameOriginProof(HttpServletRequest request) {
		String origin = request.getHeader("Origin");
		if (origin != null && !origin.isBlank()) {
			return sameOrigin(request, origin);
		}
		String fetchSite = request.getHeader("Sec-Fetch-Site");
		if (fetchSite != null && !fetchSite.isBlank()) {
			return "same-origin".equalsIgnoreCase(fetchSite);
		}
		String referer = request.getHeader("Referer");
		return referer != null && !referer.isBlank() && sameOrigin(request, referer);
	}

	private static boolean sameOrigin(HttpServletRequest request, String candidate) {
		try {
			URI uri = new URI(candidate);
			int requestPort = effectivePort(request.getScheme(), request.getServerPort());
			int candidatePort = effectivePort(uri.getScheme(), uri.getPort());
			return uri.getScheme() != null
					&& uri.getHost() != null
					&& uri.getScheme().equalsIgnoreCase(request.getScheme())
					&& uri.getHost().equalsIgnoreCase(request.getServerName())
					&& candidatePort == requestPort;
		}
		catch (URISyntaxException invalidOrigin) {
			return false;
		}
	}

	private static int effectivePort(String scheme, int port) {
		if (port > 0) {
			return port;
		}
		return "https".equalsIgnoreCase(scheme) ? 443 : 80;
	}
}
