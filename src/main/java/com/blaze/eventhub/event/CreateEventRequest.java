package com.blaze.eventhub.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        @Size(max = 2048) String xPostUrl,
        @NotBlank @Size(max = 2000) String prize,
        @NotBlank @Size(max = 80) String entryCommand,
        String startsAt,
        String endsAt,
        @Size(max = 255) String creatorChannelSlug) {

    public CreateEventRequest(
            String title,
            String description,
            String prize,
            String entryCommand,
            String startsAt,
            String endsAt,
            String creatorChannelSlug) {
        this(title, description, null, prize, entryCommand, startsAt, endsAt, creatorChannelSlug);
    }
}
