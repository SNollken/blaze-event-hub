package com.blaze.eventhub.common;

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
 * e Permissions-Policy.
 *
 * Politica de cache em 3 camadas (SPA segura para deploy):
 * - /api/**        → no-store (dados sempre frescos, sem cache)
 * - /assets/**     → public, max-age=1ano, immutable (arquivos com hash no nome;
 *                    conteudo novo = nome novo, entao cache eterno é seguro)
 * - demais rotas   → no-cache (HTML/entry points sempre revalidados; evita que um
 *                    browser com index.html velho aponte para assets que um novo
 *                    deploy removeu — a classica tela branca pos-redeploy)
 *
 * CSP permite 'unsafe-inline' para scripts/estilos — necessario pelas overlays do
 * runtime OBS que geram HTML inline (overlay-runtime.html/js estatico).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BrowserSecurityFilter extends OncePerRequestFilter {

	private static final String CSP =
			"default-src 'self'; " +
			"script-src 'self' 'unsafe-inline'; " +
			"style-src 'self' 'unsafe-inline'; " +
			"font-src 'self' data:; " +
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
		String uri = request.getRequestURI();
		if (uri.startsWith("/api/")) {
			response.setHeader("Cache-Control", "no-store");
		} else if (uri.startsWith("/assets/")) {
			response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
		} else {
			response.setHeader("Cache-Control", "no-cache");
		}
		filterChain.doFilter(request, response);
	}
}
