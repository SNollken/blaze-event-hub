package com.blaze.eventhub.events;

import java.time.Instant;

public record ChatPollingCursor(
        String memberId,
        String channelId,
        String eventId,
        String lastMessageId,
        String scanCursor,
        String scanAnchorMessageId,
        Instant lastPolledAt,
        Instant lastSuccessAt,
        String lastErrorCode) {
}
