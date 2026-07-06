package com.blaze.eventhub.event;

import java.util.List;
import java.util.Optional;

public interface EventStore {

    Event save(Event event);

    Optional<Event> findById(String id);

    List<Event> findByCreatorMemberId(String memberId);

    List<Event> findByStatus(EventStatus status);

    List<Event> findAll();

    int updateStatus(String id, EventStatus newStatus);
}
