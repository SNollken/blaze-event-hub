package com.blaze.eventhub.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.blaze.eventhub.config.ApiSecurityProperties;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.oauth.TokenStore;

class ApiKeyFilterTest {

    private ApiSecurityProperties properties;
    private TokenStore tokenStore;
    private ApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ApiSecurityProperties();
        properties.setApiKey("internal-key");
        tokenStore = mock(TokenStore.class);
        when(tokenStore.current()).thenReturn(Optional.empty());
        filter = new ApiKeyFilter(properties, tokenStore);
    }

    @Test
    void allowsPublicEventReadsWithoutCredential() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/events/event-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void requiresOAuthSessionForCreatorMutation() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
    }

    @Test
    void allowsCreatorMutationWithOAuthSession() throws Exception {
        when(tokenStore.current()).thenReturn(Optional.of(new TokenSnapshot(
                "user", "creator", "Bearer", "session-access", "refresh",
                Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now())));
        MockHttpServletRequest request = request("POST", "/api/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void retainsInternalHeaderCompatibilityButRejectsBearerAsInternalKey() throws Exception {
        MockHttpServletRequest internalRequest = request("POST", "/api/events");
        internalRequest.addHeader(ApiKeyFilter.HEADER_NAME, "internal-key");
        MockFilterChain internalChain = new MockFilterChain();
        filter.doFilter(internalRequest, new MockHttpServletResponse(), internalChain);
        assertNotNull(internalChain.getRequest());

        MockHttpServletRequest bearerRequest = request("POST", "/api/events");
        bearerRequest.addHeader("Authorization", "Bearer internal-key");
        MockHttpServletResponse bearerResponse = new MockHttpServletResponse();
        MockFilterChain bearerChain = new MockFilterChain();
        filter.doFilter(bearerRequest, bearerResponse, bearerChain);
        assertNull(bearerChain.getRequest());
        assertEquals(401, bearerResponse.getStatus());

		MockHttpServletRequest browserRequest = request("POST", "/api/events");
		browserRequest.addHeader(ApiKeyFilter.HEADER_NAME, "internal-key");
		browserRequest.addHeader("Origin", "https://app.example");
		MockFilterChain browserChain = new MockFilterChain();
		filter.doFilter(browserRequest, new MockHttpServletResponse(), browserChain);
		assertNull(browserChain.getRequest());
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }
}
