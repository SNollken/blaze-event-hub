package com.blaze.eventhub.event;

public record FinalizationStart(
        EventResponse event,
        boolean alreadyFinalized,
        String attemptId) {
}
