package com.blaze.eventhub.events;

public record PollingCycleResult(
        int channelsPolled,
        int messagesSeen,
        int acceptedEntries,
        int duplicateEntries,
        int failures) {
}
