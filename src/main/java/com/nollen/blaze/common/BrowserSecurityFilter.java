package com.nollen.blaze.common;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adiciona headers de segurança padrao a todas as respostas:
 * X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Content-Security-Policy
 * e Permissions-Policy. Cache-Control: no-store para chamadas API.
 *
 * CSP permite 'unsafe-inline' para scripts/estilos — necessario pelas overlays do
 * runtime OBS que geram HTML inline (OverlayContentService).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BrowserSecurityFilter extends OncePerRequestFilter {

	private static final String CSP =
			"default-src 'self'; " +
			"script-src 'self' 'unsafe-inline'; " +
			"style-src 'self' 'unsafe-inline'; " +
			"img-src 'self' data:; " +
			"connect-src 'self'; " +
			"frame-ancestors 'none'; " +
			"object-src 'none'";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
		response.setHeader("Content-Security-Policy", CSP);
		response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
		if (request.getRequestURI().startsWith("/api/")) {
			response.setHeader("Cache-Control", "no-store");
		}
		filterChain.doFilter(request, response);
	}
}
