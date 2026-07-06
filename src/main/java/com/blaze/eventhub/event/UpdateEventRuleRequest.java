package com.blaze.eventhub.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateEventRuleRequest(
        @NotBlank String actionType,
        @Positive int thresholdAmount,
        @Positive int entries,
        Boolean isActive) {
}
