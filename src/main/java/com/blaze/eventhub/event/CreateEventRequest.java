package com.blaze.eventhub.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 2000) String prize,
        @NotBlank @Size(max = 80) String entryCommand,
        String startsAt,
        String endsAt,
        @NotBlank @Size(max = 255) String creatorChannelSlug) {
}
