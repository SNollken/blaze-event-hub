package com.blaze.eventhub.event.interest;

import java.time.Instant;

public record EventInterestResponse(
        String memberId,
        String blazeUsername,
        String displayName,
        InterestStatus status,
        Instant interestedAt,
        int lastCalculatedEntries) {
}
