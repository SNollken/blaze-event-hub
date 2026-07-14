package com.blaze.eventhub.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

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
        assertThat(properties.getChatMessageLimit()).isEqualTo(100);
        assertThat(properties.getChatMaxPagesPerPoll()).isEqualTo(20);
        assertThat(properties.getChatHistoryCoverageMaxAgeMs()).isEqualTo(25_200_000);
        assertThat(properties.isOAuthConfigured()).isTrue();
    }

    @Test
    void validatesChatMessageLimitRange() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            BlazeProperties belowMinimum = new BlazeProperties();
            belowMinimum.setChatMessageLimit(9);
            BlazeProperties aboveMaximum = new BlazeProperties();
            aboveMaximum.setChatMessageLimit(101);
            BlazeProperties noPages = new BlazeProperties();
            noPages.setChatMaxPagesPerPoll(0);
            BlazeProperties unsafeCoverageWindow = new BlazeProperties();
            unsafeCoverageWindow.setChatHistoryCoverageMaxAgeMs(59_999);

            assertThat(validator.validate(belowMinimum))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("chatMessageLimit");
            assertThat(validator.validate(aboveMaximum))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("chatMessageLimit");
            assertThat(validator.validate(noPages))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("chatMaxPagesPerPoll");
            assertThat(validator.validate(unsafeCoverageWindow))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("chatHistoryCoverageMaxAgeMs");
        }
    }

    @Test
    void toStringDoesNotLeakClientSecret() {
        assertThat(properties.toString())
                .doesNotContain("super-secret")
                .contains("[REDACTED]");
    }
}
