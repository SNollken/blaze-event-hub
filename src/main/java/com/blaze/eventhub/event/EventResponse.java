package com.blaze.eventhub.event;

import java.time.Instant;

public record EventResponse(
        String id,
        String creatorChannelId,
        String creatorChannelSlug,
        String creatorChannelDisplayName,
        String creatorChannelAvatarUrl,
        String title,
        String description,
        String prize,
        String entryCommand,
        String status,
        int finalizedParticipantCount,
        String finalizedPoolHash,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt,
        Instant openedAt,
        Instant finalizationCutoffAt,
        Instant closedAt,
        Instant completedAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.id(),
                event.creatorChannelId(),
                event.creatorChannelSlug(),
                event.creatorChannelDisplayName(),
                event.creatorChannelAvatarUrl(),
                event.title(),
                event.description(),
                event.prize(),
                event.entryCommand(),
                event.status().name().toLowerCase(),
                event.finalizedParticipantCount(),
                event.finalizedPoolHash(),
                event.startsAt(),
                event.endsAt(),
                event.createdAt(),
                event.updatedAt(),
                event.openedAt(),
                event.finalizationCutoffAt(),
                event.closedAt(),
                event.completedAt());
    }
}
