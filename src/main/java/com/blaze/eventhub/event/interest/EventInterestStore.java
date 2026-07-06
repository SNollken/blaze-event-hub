package com.blaze.eventhub.event.interest;

import java.util.List;
import java.util.Optional;

public interface EventInterestStore {

    EventInterest save(EventInterest interest);

    Optional<EventInterest> findByEventIdAndMemberId(String eventId, String memberId);

    List<EventInterest> findByEventId(String eventId);

    List<EventInterest> findByMemberId(String memberId);

    boolean existsByEventIdAndMemberId(String eventId, String memberId);

    int delete(String eventId, String memberId);
}
