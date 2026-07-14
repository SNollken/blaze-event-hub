package com.blaze.eventhub.event;

import java.time.Instant;

public record EventLifecycleStats(
        String eventId,
        String status,
        int participantCount,
        int finalizedParticipantCount,
        boolean captureActive,
        boolean canFinalize,
        boolean canDraw,
        String captureHealth,
        Instant lastPolledAt,
        Instant lastSuccessfulPollAt,
        String lastErrorCode,
        Instant openedAt,
        Instant finalizationCutoffAt,
        Instant closedAt,
        Instant completedAt) {
}
