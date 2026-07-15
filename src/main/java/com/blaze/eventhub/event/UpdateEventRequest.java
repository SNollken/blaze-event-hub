package com.blaze.eventhub.event;

import jakarta.validation.constraints.Size;

public record UpdateEventRequest(
        @Size(max = 255) String title,
        @Size(max = 4000) String description,
        @Size(max = 2048) String xPostUrl,
        @Size(max = 2000) String prize,
        @Size(max = 80) String entryCommand,
        String startsAt,
        String endsAt) {

    public UpdateEventRequest(
            String title,
            String description,
            String prize,
            String entryCommand,
            String startsAt,
            String endsAt) {
        this(title, description, null, prize, entryCommand, startsAt, endsAt);
    }
}
