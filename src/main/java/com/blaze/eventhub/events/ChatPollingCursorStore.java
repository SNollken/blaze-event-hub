package com.blaze.eventhub.events;

import java.time.Instant;
import java.util.Optional;

public interface ChatPollingCursorStore {

    Optional<ChatPollingCursor> find(String memberId, String channelId);

    void markSuccess(
            String memberId,
            String channelId,
            String eventId,
            String lastMessageId,
            Instant polledAt);

    void markBackfillProgress(
            String memberId,
            String channelId,
            String eventId,
            String lastMessageId,
            String scanCursor,
            String scanAnchorMessageId,
            Instant polledAt);

    void markFailure(
            String memberId,
            String channelId,
            String eventId,
            String errorCode,
            Instant polledAt);
}
