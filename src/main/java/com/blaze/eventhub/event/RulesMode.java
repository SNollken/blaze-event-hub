package com.blaze.eventhub.event;

public enum RulesMode {
    TIER,
    CUMULATIVE;

    public static RulesMode fromDb(String value) {
        if (value == null || value.isBlank()) {
            return TIER;
        }
        return valueOf(value.toUpperCase());
    }
}
