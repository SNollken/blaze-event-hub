package com.blaze.eventhub.oauth;

import java.util.List;
import java.util.Map;

import com.blaze.eventhub.common.ConfigurationMissingException;
import com.blaze.eventhub.common.OAuthException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.allOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"eventhub.blaze.client-id=client-id",
		"eventhub.blaze.client-secret=client-secret",
		"eventhub.blaze.redirect-uri=http://localhost:9090/api/blaze/oauth/callback",
		"eventhub.blaze.scopes=users.read,offline.access",
		"eventhub.security.api-key=dev-local-key"
})
@AutoConfigureMockMvc
class BlazeOAuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private BlazeOAuthGateway gateway;

	@MockBean
	private OAuthProfileClient profileClient;

	@MockBean
	private OAuthCredentialStore credentialStore;

	private MockHttpSession session;

	@BeforeEach
	void setUp() {
		session = new MockHttpSession();
		given(gateway.generateAuthUrl(any(OAuthGenerateAuthUrlRequest.class)))
				.willReturn(new GeneratedAuthUrl(
						"https://blaze.stream/oauth2/authorize?state=blaze-state-1",
						"blaze-state-1",
						"verifier-1"));
		given(gateway.exchangeCode(any(OAuthTokenExchangeRequest.class)))
				.willReturn(new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-1", "refresh-token-1",
						86400L, List.of("users.read", "offline.access")));
		given(gateway.refresh(any(OAuthRefreshRequest.class)))
				.willReturn(new OAuthTokenResponse("user", "user-1", "Bearer", "access-token-2", null,
						86400L, List.of("users.read", "offline.access")));
		given(profileClient.getCurrentUserProfile())
				.willReturn(Map.of(
						"id", "user-1",
						"username", "sofia",
						"displayName", "Sofia Blaze",
						"avatarUrl", "https://cdn.example.test/avatar.png",
						"refreshToken", "must-not-leak"));
	}

	@Test
	void sessionWithoutTokenIsSafeAndDisconnected() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/session").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(false))
				.andExpect(jsonPath("$.tokenPresent").value(false))
				.andExpect(jsonPath("$.refreshCredentialPresent").value(false))
				.andExpect(jsonPath("$.profilePresent").value(false))
				.andExpect(jsonPath("$.nextRecommendedAction").value("CONNECT_BLAZE"))
				.andExpect(content().string(not(containsString("access-token"))))
				.andExpect(content().string(not(containsString("refresh-token"))))
				.andExpect(content().string(not(containsString("client-secret"))))
				.andExpect(content().string(not(containsString("verifier-1"))));
	}

	@Test
	void oauthConnectionIsIsolatedByHttpSession() throws Exception {
		MockHttpSession sessionA = new MockHttpSession();
		MockHttpSession sessionB = new MockHttpSession();

		mockMvc.perform(get("/api/blaze/oauth/start").session(sessionA))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(sessionA)
						.param("code", "auth-code-a")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/oauth/session").session(sessionA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(true));
		mockMvc.perform(get("/api/blaze/oauth/session").session(sessionB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void oauthStateCannotBeUsedByAnotherHttpSession() throws Exception {
		MockHttpSession sessionA = new MockHttpSession();
		MockHttpSession sessionB = new MockHttpSession();

		mockMvc.perform(get("/api/blaze/oauth/start").session(sessionA))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(sessionB)
						.param("code", "auth-code-b")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("OAUTH_CALLBACK_INVALID"));
	}

	@Test
	void oauthStateIsConsumedBeforeTokenExchange() throws Exception {
		given(gateway.exchangeCode(any(OAuthTokenExchangeRequest.class)))
				.willThrow(new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED", "exchange failed"));

		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BLAZE_TOKEN_EXCHANGE_REJECTED"));

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("OAUTH_CALLBACK_INVALID"));

		verify(gateway, times(1)).exchangeCode(any(OAuthTokenExchangeRequest.class));
	}

	@Test
	void browserCallbackRedirectsToLoginAfterSuccessWithoutSensitiveValues() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "text/html"))
				.andExpect(status().isSeeOther())
				.andExpect(header().string(HttpHeaders.LOCATION, "/login?oauth=success"))
				.andExpect(content().string(""));
	}

	@Test
	void browserCallbackRedirectsToLoginAfterOAuthErrorWithoutSensitiveValues() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());
		given(gateway.exchangeCode(any(OAuthTokenExchangeRequest.class)))
				.willThrow(new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED",
						"code=auth-code-1 state=blaze-state-1 verifier=verifier-1 "
								+ "accessToken=access-token-1 refreshToken=refresh-token-1 clientSecret=client-secret"));

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "text/html"))
				.andExpect(status().isSeeOther())
				.andExpect(header().string(HttpHeaders.LOCATION, "/login?oauth=error"))
				.andExpect(content().string(""));
	}

	@Test
	void browserCallbackRedirectsToLoginAfterConfigurationFailureWithoutSensitiveValues() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());
		org.mockito.BDDMockito.willThrow(new ConfigurationMissingException(
				"EVENTHUB_CREDENTIAL_ENCRYPTION_KEY sensitive-marker"))
				.given(credentialStore)
				.save(any(String.class), any(TokenSnapshot.class));

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "text/html"))
				.andExpect(status().isSeeOther())
				.andExpect(header().string(HttpHeaders.LOCATION, "/login?oauth=error"))
				.andExpect(content().string(""));
	}

	@Test
	void callbackReturnsSafeJsonWhenRequested() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("stored"))
				.andExpect(jsonPath("$.refreshTokenPresent").value(true))
				.andExpect(jsonPath("$.profilePresent").value(true))
				.andExpect(jsonPath("$.profile.displayName").value("Sofia Blaze"))
				.andExpect(content().string(not(containsString("auth-code-1"))))
				.andExpect(content().string(not(containsString("blaze-state-1"))))
				.andExpect(content().string(not(containsString("verifier-1"))))
				.andExpect(content().string(not(containsString("access-token-1"))))
				.andExpect(content().string(not(containsString("refresh-token-1"))))
				.andExpect(content().string(not(containsString("client-secret"))));
	}

	@Test
	void successfulCallbackRotatesSessionId() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());
		String sessionIdBeforeLogin = session.getId();

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isOk());

		assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
	}

	@Test
	void refreshPreservesSessionAndNeverReturnsTokens() throws Exception {
		connectWithJsonCallback();

		mockMvc.perform(post("/api/blaze/oauth/refresh")
						.session(session)
						.header("X-Nollen-Api-Key", "dev-local-key"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshed").value(true))
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.refreshCredentialPresent").value(true))
				.andExpect(jsonPath("$.profilePresent").value(true))
				.andExpect(content().string(not(containsString("access-token-2"))))
				.andExpect(content().string(not(containsString("refresh-token-1"))))
				.andExpect(content().string(not(containsString("client-secret"))));
	}

	@Test
	void disconnectClearsLocalSessionAndProfile() throws Exception {
		connectWithJsonCallback();

		mockMvc.perform(post("/api/blaze/oauth/disconnect")
						.session(session)
						.header("X-Nollen-Api-Key", "dev-local-key"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disconnected").value(true))
				.andExpect(jsonPath("$.connected").value(false))
				.andExpect(jsonPath("$.tokenPresent").value(false))
				.andExpect(jsonPath("$.profilePresent").value(false));

		mockMvc.perform(get("/api/blaze/oauth/session").session(new MockHttpSession()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(false))
				.andExpect(jsonPath("$.profilePresent").value(false));
	}

	@Test
	void disconnectInvalidatesEntireHttpSession() throws Exception {
		connectWithJsonCallback();

		mockMvc.perform(post("/api/blaze/oauth/disconnect")
						.session(session)
						.header("X-Nollen-Api-Key", "dev-local-key"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("JSESSIONID="),
						containsString("Max-Age=0"),
						containsString("Secure"),
						containsString("HttpOnly"),
						containsString("SameSite=Lax"))));

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void refreshWithoutCredentialFailsSafely() throws Exception {
		mockMvc.perform(post("/api/blaze/oauth/refresh")
						.session(session)
						.header("X-Nollen-Api-Key", "dev-local-key"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().string(not(containsString("access-token"))))
				.andExpect(content().string(not(containsString("refresh-token"))))
				.andExpect(content().string(not(containsString("client-secret"))));
	}

	@Test
	void jsonCallbackErrorSanitizesRemoteSensitiveText() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());
		given(gateway.exchangeCode(any(OAuthTokenExchangeRequest.class)))
				.willThrow(new OAuthException(400, "BLAZE_TOKEN_EXCHANGE_REJECTED",
						"remote error code=auth-code-1 state=blaze-state-1 codeVerifier=verifier-1 authorizationUrl=https://blaze.stream/oauth?state=blaze-state-1 accessToken=access-token-1 refreshToken=refresh-token-1 clientSecret=client-secret"));

		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(not(containsString("auth-code-1"))))
				.andExpect(content().string(not(containsString("blaze-state-1"))))
				.andExpect(content().string(not(containsString("verifier-1"))))
				.andExpect(content().string(not(containsString("access-token-1"))))
				.andExpect(content().string(not(containsString("refresh-token-1"))))
				.andExpect(content().string(not(containsString("client-secret"))))
				.andExpect(content().string(not(containsString("https://blaze.stream/oauth"))));
	}

	private void connectWithJsonCallback() throws Exception {
		mockMvc.perform(get("/api/blaze/oauth/start").session(session))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/blaze/oauth/callback")
						.session(session)
						.param("code", "auth-code-1")
						.param("state", "blaze-state-1")
						.header(HttpHeaders.ACCEPT, "application/json"))
				.andExpect(status().isOk());
	}
}
