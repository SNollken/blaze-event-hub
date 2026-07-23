package com.blaze.eventhub.event;

import java.util.List;
import java.util.Optional;

/**
 * Persistência das regras de ação por evento.
 */
public interface EventActionRuleStore {

    List<EventActionRule> findByEventId(String eventId);

    Optional<EventActionRule> findByEventIdAndType(String eventId, ActionType actionType);

    void save(EventActionRule rule);

    void saveAll(List<EventActionRule> rules);

    void deleteByEventId(String eventId);
}
