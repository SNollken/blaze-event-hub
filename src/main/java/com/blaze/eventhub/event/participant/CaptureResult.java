package com.blaze.eventhub.event.participant;

public record CaptureResult(CaptureStatus status, String eventId, String reason) {

    static CaptureResult accepted(String eventId) {
        return new CaptureResult(CaptureStatus.ACCEPTED, eventId, "entry_accepted");
    }

    static CaptureResult duplicate(String eventId) {
        return new CaptureResult(CaptureStatus.DUPLICATE, eventId, "entry_already_registered");
    }

    static CaptureResult ignored(String reason) {
        return new CaptureResult(CaptureStatus.IGNORED, null, reason);
    }
}
