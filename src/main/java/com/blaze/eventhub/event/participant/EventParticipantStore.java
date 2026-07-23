package com.blaze.eventhub.event.participant;

import java.util.List;

public interface EventParticipantStore {

    boolean saveIfAbsent(EventParticipant participant);

    List<EventParticipant> findByEventId(String eventId);

    int countByEventId(String eventId);

    int getRawActionCount(String eventId, String blazeUserId, String actionType);

    void incrementRawActionCount(String eventId, String blazeUserId, String actionType);

    void updateEntryWeight(String eventId, String blazeUserId, int newWeight);
}