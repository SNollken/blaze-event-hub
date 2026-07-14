package com.blaze.eventhub.event;

import java.time.Instant;

public record Event(
        String id,
        String creatorMemberId,
        String creatorBlazeUserId,
        String creatorChannelId,
        String creatorChannelSlug,
        String creatorChannelDisplayName,
        String creatorChannelAvatarUrl,
        String title,
        String description,
        String prize,
        String entryCommand,
        EventStatus status,
        int finalizedParticipantCount,
        String finalizedPoolHash,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt,
        Instant openedAt,
        Instant finalizationCutoffAt,
        String finalizationAttemptId,
        Instant closedAt,
        Instant completedAt) {
}
