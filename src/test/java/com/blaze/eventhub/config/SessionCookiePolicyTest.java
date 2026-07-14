package com.blaze.eventhub.config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"server.servlet.session.cookie.name=BEH_SESSION",
		"server.servlet.session.cookie.path=/hub",
		"server.servlet.session.cookie.http-only=true",
		"server.servlet.session.cookie.secure=true",
		"server.servlet.session.cookie.same-site=Lax"
})
class SessionCookiePolicyTest {

	@Autowired
	private SessionCookiePolicy policy;

	@Test
	void expiredCookiePreservesConfiguredSecurityAttributes() {
		String header = policy.expiredCookie().toString();

		assertThat(header)
				.contains("BEH_SESSION=")
				.contains("Path=/hub")
				.contains("Max-Age=0")
				.contains("Secure")
				.contains("HttpOnly")
				.contains("SameSite=Lax");
	}
}
