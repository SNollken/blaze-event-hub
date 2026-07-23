package com.blaze.eventhub.event;

/**
 * Tipos de ação que podem gerar entradas em um giveaway.
 * Cada tipo corresponde a um gatilho diferente na Blaze.
 */
public enum ActionType {
    CHAT("chat"),
    VOTE("vote"),
    SUB("sub"),
    GIFTED_SUB("gifted_sub"),
    FOLLOW("follow"),
    DONATION("donation");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ActionType fromString(String value) {
        if (value == null) {
            return CHAT;
        }
        for (ActionType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return CHAT;
    }
}
