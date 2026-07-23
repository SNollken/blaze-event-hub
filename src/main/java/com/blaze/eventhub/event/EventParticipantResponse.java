package com.blaze.eventhub.event;

import java.time.Instant;

import com.blaze.eventhub.event.participant.EventParticipant;

public record EventParticipantResponse(
        String blazeUserId,
        String blazeUsername,
        String displayName,
        String actionType,
        int entryWeight,
        Instant enteredAt) {

    public static EventParticipantResponse from(EventParticipant participant) {
        return new EventParticipantResponse(
                participant.blazeUserId(),
                participant.blazeUsername(),
                participant.displayName(),
                participant.actionType() != null ? participant.actionType() : "chat",
                participant.entryWeight(),
                participant.enteredAt());
    }
}
