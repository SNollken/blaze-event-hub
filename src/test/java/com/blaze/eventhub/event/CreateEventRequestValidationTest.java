package com.blaze.eventhub.event;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateEventRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCreationWithoutAChannelSelectedByTheBrowser() {
        CreateEventRequest request = new CreateEventRequest(
                "Giveaway da comunidade",
                "Entradas pelo chat",
                "Gift card",
                "!participar",
                null,
                null,
                null);

        assertThat(validator.validate(request))
                .noneMatch(violation -> violation.getPropertyPath().toString().equals("creatorChannelSlug"));
    }
}
