package com.blaze.eventhub.event;

import java.util.List;

public interface EventRuleStore {

    List<EventRule> findByEventId(String eventId);

    EventRule save(EventRule rule);

    int update(EventRule rule);

    int delete(String id);
}
