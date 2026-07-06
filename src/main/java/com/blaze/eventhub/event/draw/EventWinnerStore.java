package com.blaze.eventhub.event.draw;

import java.util.List;
import java.util.Optional;

public interface EventWinnerStore {

    EventWinner save(EventWinner winner);

    Optional<EventWinner> findByEventId(String eventId);

    List<EventWinner> findAllByEventId(String eventId);
}
