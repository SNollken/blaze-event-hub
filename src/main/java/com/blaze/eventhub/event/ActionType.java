package com.blaze.eventhub.event;

public enum ActionType {
    VOTE,
    SUB,
    GIFTED_SUB,
    DONATION,
    MANUAL;

    public static ActionType fromDb(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        return valueOf(value.toUpperCase());
    }
}
