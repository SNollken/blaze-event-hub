package com.blaze.eventhub.event.detection;

import java.util.List;
import java.util.Optional;

public interface DetectedActionStore {
    DetectedAction save(DetectedAction action);
    Optional<DetectedAction> findByIdempotencyHash(String hash);
    List<DetectedAction> findByEventId(String eventId);
    List<DetectedAction> findByMemberId(String memberId);
    List<DetectedAction> findByEventIdAndMemberId(String eventId, String memberId);
}
