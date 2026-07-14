package com.blaze.eventhub.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.blaze.eventhub.config.ApiSecurityProperties;
import com.blaze.eventhub.oauth.TokenStore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Nollen-Api-Key";

    private static final List<String> ALWAYS_PUBLIC_PREFIXES = List.of(
            "/api/health",
            "/api/status",
            "/api/blaze/oauth/callback",
            "/api/blaze/oauth/start",
            "/api/blaze/oauth/session",
            "/api/public/");

    private final ApiSecurityProperties properties;
    private final TokenStore tokenStore;

    public ApiKeyFilter(ApiSecurityProperties properties, TokenStore tokenStore) {
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        if (ALWAYS_PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        return HttpMethod.GET.matches(request.getMethod()) && isPublicRead(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (hasValidInternalKey(request, properties) || authenticatedSession()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Conecte sua conta Blaze para continuar\"}");
    }

    private boolean authenticatedSession() {
        try {
            return tokenStore.current()
                    .filter(token -> !token.accessTokenBlank())
                    .isPresent();
        } catch (RuntimeException noActiveSession) {
            return false;
        }
    }

    static boolean hasValidInternalKey(HttpServletRequest request, ApiSecurityProperties properties) {
        if (isBrowserRequest(request)) {
            return false;
        }
        String requestKey = request.getHeader(HEADER_NAME);
        String configuredKey = properties.getApiKey();
        if (configuredKey == null || configuredKey.isBlank() || requestKey == null || requestKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                requestKey.getBytes(StandardCharsets.UTF_8));
    }

    static boolean isBrowserRequest(HttpServletRequest request) {
        return hasText(request.getHeader("Origin"))
                || hasText(request.getHeader("Sec-Fetch-Site"))
                || hasText(request.getHeader("Sec-Fetch-Mode"))
                || hasText(request.getHeader("Sec-Fetch-Dest"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isPublicRead(String path) {
        if ("/api/events".equals(path)) {
            return true;
        }
        if (path.matches("/api/events/[^/]+")) {
            return true;
        }
        if (path.matches("/api/events/[^/]+/(stats|winner|result)")) {
            return true;
        }
        return false;
    }
}
