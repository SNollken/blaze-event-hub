package com.nollen.blaze.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nollen.blaze.oauth.BlazeOAuthGateway;
import com.nollen.blaze.oauth.GeneratedAuthUrl;
import com.nollen.blaze.oauth.OAuthGenerateAuthUrlRequest;
import com.nollen.blaze.oauth.OAuthProfileClient;
import com.nollen.blaze.oauth.OAuthRefreshRequest;
import com.nollen.blaze.oauth.OAuthTokenExchangeRequest;
import com.nollen.blaze.oauth.OAuthTokenResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.security.rate-limit-per-minute=2",
		"nollen.blaze.client-id=client-id",
		"nollen.blaze.client-secret=client-secret",
		"nollen.blaze.redirect-uri=http://localhost:8080/api/blaze/oauth/callback",
		"nollen.blaze.scopes=users.read,offline.access"
})
class RateLimitFilterTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RateLimitFilter rateLimitFilter;

	@MockBean
	private BlazeOAuthGateway gateway;

	@MockBean
	private OAuthProfileClient profileClient;

	@BeforeEach
	void setUp() {
		rateLimitFilter.setClock(Clock.fixed(Instant.parse("2026-08-02T12:00:00Z"), ZoneOffset.UTC));
		given(gateway.generateAuthUrl(any(OAuthGenerateAuthUrlRequest.class)))
				.willReturn(new GeneratedAuthUrl(
						"https://blaze.stream/oauth2/authorize?state=blaze-state-1",
						"blaze-state-1",
						"verifier-1"));
		given(gateway.exchangeCode(any(OAuthTokenExchangeRequest.class)))
				.willReturn(new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-1", "refresh-token-1",
						86400L, java.util.List.of("users.read", "offline.access")));
		given(gateway.refresh(any(OAuthRefreshRequest.class)))
				.willReturn(new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-2", null,
						86400L, java.util.List.of("users.read", "offline.access")));
	}

	@Test
	void blocksWhenLimitExceededAndAllowsAfterWindowElapses() throws Exception {
		// limite 2/min: os dois primeiros passam
		mockMvc.perform(post("/api/blaze/oauth/start")).andExpect(status().isOk());
		mockMvc.perform(post("/api/blaze/oauth/start")).andExpect(status().isOk());

		// terceiro é bloqueado com 429 + Retry-After
		mockMvc.perform(post("/api/blaze/oauth/start"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "60"))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));

		// janela de 1 minuto expira -> volta a permitir
		rateLimitFilter.setClock(Clock.fixed(Instant.parse("2026-08-02T12:01:01Z"), ZoneOffset.UTC));
		mockMvc.perform(post("/api/blaze/oauth/start")).andExpect(status().isOk());
	}

	@Test
	void callbackAndSessionAreNotRateLimited() throws Exception {
		// GETs (callback público + session) nunca são bloqueados por limite de frequência.
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
							.get("/api/blaze/oauth/callback").param("code", "c").param("state", "s"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
							.get("/api/blaze/oauth/session"))
					.andExpect(status().isOk());
		}
	}
}
