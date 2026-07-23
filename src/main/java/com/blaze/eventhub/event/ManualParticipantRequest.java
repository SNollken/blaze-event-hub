package com.blaze.eventhub.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualParticipantRequest(
        @NotBlank @Size(max = 50) String blazeUsername) {
}