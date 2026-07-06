package com.blaze.eventhub.event.interest;

public enum InterestStatus {
    INTERESTED,
    ELIGIBLE,
    NOT_ELIGIBLE,
    WITHDRAWN,
    BANNED;

    public static InterestStatus fromDb(String value) {
        if (value == null || value.isBlank()) {
            return INTERESTED;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INTERESTED;
        }
    }
}
