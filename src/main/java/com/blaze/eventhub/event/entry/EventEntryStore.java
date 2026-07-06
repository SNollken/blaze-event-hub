package com.blaze.eventhub.event.entry;

import java.util.List;

public interface EventEntryStore {
    EventEntry save(EventEntry entry);
    List<EventEntry> findByEventId(String eventId);
    List<EventEntry> findByMemberId(String memberId);
    List<EventEntry> findByEventIdAndMemberId(String eventId, String memberId);
    int countByEventIdAndMemberId(String eventId, String memberId);
    int countByEventId(String eventId);
}
