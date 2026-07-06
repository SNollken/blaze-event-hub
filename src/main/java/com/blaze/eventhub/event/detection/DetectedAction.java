package com.blaze.eventhub.event.detection;

import java.time.Instant;

public record DetectedAction(
    String id,
    String idempotencyHash,
    String eventId,
    String memberId,
    String actionType,
    String targetChannelId,
    String actorBlazeUserId,
    String actorUsername,
    String actorWalletAddress,
    int amount,
    String rawPayload,
    Instant detectedAt,
    boolean processed,
    Instant createdAt) {}
