package com.blaze.eventhub.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.blaze.eventhub.oauth.BlazeOAuthGateway;
import com.blaze.eventhub.oauth.GeneratedAuthUrl;
import com.blaze.eventhub.oauth.OAuthGenerateAuthUrlRequest;
import com.blaze.eventhub.oauth.OAuthProfileClient;
import com.blaze.eventhub.oauth.OAuthRefreshRequest;
import com.blaze.eventhub.oauth.OAuthTokenExchangeRequest;
import com.blaze.eventhub.oauth.OAuthTokenResponse;

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
		"beh.security.rate-limit-per-minute=2",
		"beh.blaze.client-id=client-id",
		"beh.blaze.client-secret=client-secret",
		"beh.blaze.redirect-uri=http://localhost:8080/api/blaze/oauth/callback",
		"beh.blaze.scopes=users.read,offline.access"
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
	void spoofedXForwardedForCannotBypassTheLimit() throws Exception {
		// O edge do Render ANEXA o IP real do cliente como ÚLTIMA entrada de
		// X-Forwarded-For. O filtro precisa chavear por essa entrada, não pela
		// primeira (controlada pelo atacante): com o comportamento antigo,
		// bastava mandar um XFF aleatório por request para ganhar um bucket
		// novo e burlar o limite.
		mockMvc.perform(post("/api/blaze/oauth/start").header("X-Forwarded-For", "spoofed-1, 10.0.0.1"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/blaze/oauth/start").header("X-Forwarded-For", "spoofed-2, 10.0.0.1"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/blaze/oauth/start").header("X-Forwarded-For", "spoofed-3, 10.0.0.1"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "60"));
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
