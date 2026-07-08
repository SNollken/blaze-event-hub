package com.blaze.eventhub.event;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        @Size(max = 80) String prizeType,
        @Size(max = 2000) String prizeDescription,
        String rulesMode,
        int maxEntriesPerParticipant,
        boolean requiresInterestBeforeAction,
        String startsAt,
        String endsAt,
        @NotBlank String creatorChannelId,
        @NotEmpty @Valid List<CreateEventRuleRequest> rules) {
}
