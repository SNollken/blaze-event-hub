package com.blaze.eventhub.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAuthorizationUrlGeneratorTests {

	private final LocalAuthorizationUrlGenerator generator = new LocalAuthorizationUrlGenerator();

	@Test
	void s256CodeChallengeProducesValidBase64Url() {
		String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
		String challenge = LocalAuthorizationUrlGenerator.s256CodeChallenge(verifier);
		assertThat(challenge).isNotBlank();
		assertThat(challenge).doesNotContain("=");
		assertThat(challenge).doesNotContain("+");
		assertThat(challenge).doesNotContain("/");
	}

	@Test
	void buildAuthorizationUrlContainsAllRequiredParameters() {
		String url = LocalAuthorizationUrlGenerator.buildAuthorizationUrl(
				"client-123",
				"http://localhost:9090/callback",
				java.util.List.of("users.read", "offline.access"),
				"state-abc",
				"challenge-xyz");

		assertThat(url).startsWith("https://blaze.stream/oauth2/authorize?");
		assertThat(url).contains("response_type=code");
		assertThat(url).contains("client_id=client-123");
		assertThat(url).contains("redirect_uri=");
		assertThat(url).contains("scope=users.read+offline.access");
		assertThat(url).contains("state=state-abc");
		assertThat(url).contains("code_challenge_method=S256");
		assertThat(url).contains("code_challenge=challenge-xyz");
	}

	@Test
	void generateProducesValidAuthorizationUrl() {
		GeneratedAuthUrl result = generator.generate(
				"client-id",
				"http://localhost:9090/callback",
				java.util.List.of("users.read"));

		assertThat(result.authorizationUrl()).isNotNull();
		assertThat(result.authorizationUrl()).startsWith("https://blaze.stream/oauth2/authorize?");
		assertThat(result.state()).isNotNull();
		assertThat(result.state()).hasSizeGreaterThan(20);
		assertThat(result.codeVerifier()).isNotNull();
		assertThat(result.codeVerifier()).hasSizeGreaterThan(20);
		assertThat(result.authorizationUrl()).contains("state=" + result.state());
	}
}
