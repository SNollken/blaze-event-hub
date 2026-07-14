package com.blaze.eventhub.event;

public enum EventStatus {
    DRAFT,
    OPEN,
    FINALIZING,
    CLOSED,
    COMPLETED,
    CANCELLED;

    public static EventStatus fromDb(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        return valueOf(value.toUpperCase());
    }
}
