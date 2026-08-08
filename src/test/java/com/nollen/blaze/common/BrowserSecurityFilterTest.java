package com.nollen.blaze.common;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserSecurityFilterTest {

	private final BrowserSecurityFilter filter = new BrowserSecurityFilter();

	@Test
	void addsSecurityHeadersToResponse() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });

		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
		assertThat(response.getHeader("Content-Security-Policy")).isNotNull();
		assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
		// @fontsource self-hosted fonts load from /assets plus small inlined data: subsets
		assertThat(response.getHeader("Content-Security-Policy")).contains("font-src 'self' data:");
		assertThat(response.getHeader("Permissions-Policy")).isNotNull();
		assertThat(response.getHeader("frame-ancestors")).isNull();
	}

	@Test
	void addsNoStoreCacheControlForApiPaths() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/blaze/setup");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
	}

	@Test
	void addsNoCacheForHtmlEntryPoints() throws ServletException, IOException {
		for (String path : new String[] {"/", "/dashboard", "/blaze", "/events", "/overlay/abc123"}) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilterInternal(request, response, (req, resp) -> { });
			assertThat(response.getHeader("Cache-Control")).as("Cache-Control for %s", path).isEqualTo("no-cache");
		}
	}

	@Test
	void addsImmutableLongLivedCacheForHashedAssets() throws ServletException, IOException {
		for (String path : new String[] {"/assets/index-2mK_JS8L.js", "/assets/index-pQldHEI4.css", "/assets/funnel-sans-latin-400-normal.woff2"}) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilterInternal(request, response, (req, resp) -> { });
			assertThat(response.getHeader("Cache-Control")).as("Cache-Control for %s", path)
					.isEqualTo("public, max-age=31536000, immutable");
		}
	}
}
