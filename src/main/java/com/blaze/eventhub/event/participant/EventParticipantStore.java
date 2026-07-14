package com.blaze.eventhub.event.participant;

import java.util.List;

public interface EventParticipantStore {

    boolean saveIfAbsent(EventParticipant participant);

    List<EventParticipant> findByEventId(String eventId);

    int countByEventId(String eventId);
}
