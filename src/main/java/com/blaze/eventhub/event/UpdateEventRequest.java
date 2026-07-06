package com.blaze.eventhub.event;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateEventRequest(
        @Size(max = 255) String title,
        @Size(max = 4000) String description,
        @Size(max = 80) String prizeType,
        @Size(max = 2000) String prizeDescription,
        String rulesMode,
        @PositiveOrZero int maxEntriesPerParticipant,
        Boolean requiresInterestBeforeAction,
        String startsAt,
        String endsAt) {
}
