package com.blaze.eventhub.event;

import java.util.List;
import java.util.Optional;

public interface EventStore {

    Event insert(Event event);

    int updateDraft(Event event);

    Optional<Event> findById(String id);

    Optional<Event> findByIdForUpdate(String id);

    List<Event> findByCreatorMemberId(String memberId);

    List<Event> findByStatus(EventStatus status);

    List<Event> findCapturingByChannelId(String channelId);

    List<Event> findAll();

    int cancelEvent(String id, java.time.Instant cancelledAt);

    int openEvent(String id, String activeCaptureKey, java.time.Instant openedAt);

    int beginFinalization(String id, java.time.Instant cutoffAt, String attemptId);

    int abortFinalization(String id, String attemptId, java.time.Instant updatedAt);

    int finalizeEvent(String id, String attemptId, java.time.Instant closedAt, int participantCount, String poolHash);

    int recoverStaleFinalizations(java.time.Instant staleBefore, java.time.Instant updatedAt);

    int completeEvent(String id, java.time.Instant completedAt);
}
