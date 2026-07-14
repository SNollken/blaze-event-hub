package com.blaze.eventhub.common;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.blaze.eventhub.config.ApiSecurityProperties;
import com.blaze.eventhub.oauth.TokenStore;
import com.blaze.eventhub.oauth.TokenSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowserSecurityFilterTest {

	private TokenStore tokenStore;
	private BrowserSecurityFilter filter;

	@BeforeEach
	void setUp() {
		tokenStore = mock(TokenStore.class);
		when(tokenStore.current()).thenReturn(Optional.empty());
		ApiSecurityProperties properties = new ApiSecurityProperties();
		properties.setApiKey("internal-key");
		filter = new BrowserSecurityFilter(properties, tokenStore);
	}

	@Test
	void addsBrowserSecurityHeadersToEveryResponse() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.setSecure(true);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader("Content-Security-Policy"))
				.contains("default-src 'self'")
				.contains("frame-ancestors 'none'")
				.contains("object-src 'none'")
				.contains("https://fonts.googleapis.com")
				.contains("https://fonts.gstatic.com");
		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		assertThat(response.getHeader("Permissions-Policy"))
				.isEqualTo("camera=(), microphone=(), geolocation=()");
		assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
		assertThat(response.getHeader("Strict-Transport-Security"))
				.isEqualTo("max-age=31536000; includeSubDomains");
	}

	@Test
	void rejectsCrossOriginMutationAuthenticatedBySessionCookie() throws Exception {
		when(tokenStore.current()).thenReturn(Optional.of(new TokenSnapshot(
				"user", "creator", "Bearer", "session-access", "refresh",
				Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now())));
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
		request.setRequestURI("/api/events");
		request.addHeader("Origin", "https://attacker.example");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("CSRF_VALIDATION_FAILED");
	}

	@Test
	void allowsSameOriginMutationAuthenticatedBySessionCookie() throws Exception {
		when(tokenStore.current()).thenReturn(Optional.of(new TokenSnapshot(
				"user", "creator", "Bearer", "session-access", "refresh",
				Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now())));
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
		request.setRequestURI("/api/events");
		request.setScheme("https");
		request.setServerName("eventhub.example");
		request.setServerPort(443);
		request.addHeader("Origin", "https://eventhub.example");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(response.getStatus()).isEqualTo(200);
	}
}
