package com.blaze.eventhub.event.draw;

import java.time.Instant;

public record EventResultResponse(
        String eventId,
        String winnerUsername,
        String winnerDisplayName,
        String drawSeed,
        String drawMethod,
        String poolHash,
        int participantCount,
        Instant selectedAt) {

    public static EventResultResponse from(EventWinner winner) {
        return new EventResultResponse(
                winner.eventId(),
                winner.winnerUsername(),
                winner.winnerDisplayName(),
                winner.drawSeed(),
                winner.drawMethod(),
                winner.poolHash(),
                winner.participantCount(),
                winner.selectedAt());
    }
}
