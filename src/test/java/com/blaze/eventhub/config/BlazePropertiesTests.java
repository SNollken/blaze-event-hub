package com.blaze.eventhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eventhub.blaze.client-id=test-client",
        "eventhub.blaze.client-secret=super-secret",
        "eventhub.blaze.redirect-uri=http://localhost:8080/api/blaze/oauth/callback",
        "eventhub.blaze.scopes=users.read,offline.access"
})
class BlazePropertiesTests {

    @Autowired
    private BlazeProperties properties;

    @Test
    void loadsConfiguredPlaceholders() {
        assertThat(properties.getClientId()).isEqualTo("test-client");
        assertThat(properties.getScopes()).containsExactly("users.read", "offline.access");
        assertThat(properties.isOAuthConfigured()).isTrue();
    }

    @Test
    void toStringDoesNotLeakClientSecret() {
        assertThat(properties.toString())
                .doesNotContain("super-secret")
                .contains("[REDACTED]");
    }
}
