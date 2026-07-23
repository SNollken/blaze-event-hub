package com.blaze.eventhub.event;

import java.util.List;
import java.util.Optional;

/**
 * Persistência dos tiers de ação por evento.
 */
public interface EventActionTierStore {

    List<EventActionTier> findByEventId(String eventId);

    List<EventActionTier> findByEventIdAndType(String eventId, ActionType actionType);

    Optional<EventActionTier> findByEventIdAndTypeAndThreshold(String eventId, ActionType actionType, int threshold);

    void save(EventActionTier tier);

    void saveAll(List<EventActionTier> tiers);

    void deleteByEventId(String eventId);

    void deleteByEventIdAndType(String eventId, ActionType actionType);
}