package com.blaze.eventhub.event;

import java.time.Instant;

public record Event(
        String id,
        String creatorMemberId,
        String creatorBlazeUserId,
        String creatorChannelId,
        String title,
        String description,
        String prizeType,
        String prizeDescription,
        EventStatus status,
        RulesMode rulesMode,
        int maxEntriesPerParticipant,
        boolean requiresInterestBeforeAction,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        Instant completedAt) {
}
