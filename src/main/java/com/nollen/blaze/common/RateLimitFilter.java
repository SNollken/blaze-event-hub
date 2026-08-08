package com.nollen.blaze.common;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nollen.blaze.config.ApiSecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit simples por IP (janela deslizante em memória) para endpoints de OAuth:
 * protege start/refresh/disconnect contra abuso mesmo sem sessão autenticada e sem
 * depender do API key (callback é público e os POSTs de OAuth são sensíveis).
 *
 * ponytail: janela em memória por instância (perde histórico em restart/multi-instância);
 * trocar por Redis/DB quando houver múltiplas instâncias.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	static final int DEFAULT_LIMIT_PER_MINUTE = 30;
	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final String HEADER_RETRY_AFTER = "Retry-After";

	private final ApiSecurityProperties properties;
	private Clock clock;
	private final Map<String, long[]> hits = new ConcurrentHashMap<>();

	public RateLimitFilter(ApiSecurityProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/** Usado apenas por testes para controlar a janela de tempo. */
	void setClock(Clock clock) {
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (!path.startsWith("/api/blaze/oauth/")) {
			return true;
		}
		// GET session é leve e seguro; callback precisa aceitar o redirect do navegador
		// (GET com query params) e não pode ser bloqueado por limite de frequência.
		return request.getMethod().equals("GET") || path.endsWith("/callback");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String key = clientKey(request);
		Instant now = clock.instant();
		int limit = properties.getRateLimitPerMinute();
		if (limit <= 0 || allow(key, now, limit)) {
			filterChain.doFilter(request, response);
			return;
		}
		response.setStatus(429);
		response.setContentType("application/json");
		response.setHeader(HEADER_RETRY_AFTER, "60");
		response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Muitas requisicoes. Tente novamente em instantes.\"}");
	}

	/** Registra o hit e devolve true se o total na janela de 1 min ficou <= limite. */
	private boolean allow(String key, Instant now, int limit) {
		long[] window = hits.computeIfAbsent(key, k -> new long[] { now.toEpochMilli(), 0L });
		synchronized (window) {
			long windowStart = now.toEpochMilli() - WINDOW.toMillis();
			if (window[0] < windowStart) {
				window[0] = now.toEpochMilli();
				window[1] = 0L;
			}
			window[1]++;
			return window[1] <= limit;
		}
	}

	/**
	 * Chave do cliente para o rate limit. Atrás do Render (ou qualquer reverse
	 * proxy único confiável), o edge ANEXA o IP real do cliente como ÚLTIMA
	 * entrada de X-Forwarded-For. Pegar a primeira entrada deixaria um atacante
	 * enviar o próprio header X-Forwarded-For e ganhar um bucket novo por
	 * request, burlando o limite trivialmente. Sem XFF, cai para o peer TCP.
	 */
	private static String clientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			String[] hops = forwarded.split(",");
			String last = hops[hops.length - 1].trim();
			if (!last.isEmpty()) {
				return last;
			}
		}
		return request.getRemoteAddr();
	}
}
