package com.blaze.eventhub.event.interest;

import java.time.Instant;

public record EventInterest(
        String id,
        String eventId,
        String memberId,
        InterestStatus status,
        Instant interestedAt,
        int lastCalculatedEntries,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
