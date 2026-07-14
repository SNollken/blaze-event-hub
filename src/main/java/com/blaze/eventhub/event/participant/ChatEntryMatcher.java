package com.blaze.eventhub.event.participant;

import java.util.Locale;

public final class ChatEntryMatcher {

    private ChatEntryMatcher() {
    }

    public static boolean matches(String configuredCommand, String message) {
        String command = normalizeCommand(configuredCommand);
        String candidate = normalize(message);
        return !command.isEmpty() && !candidate.isEmpty() && candidate.equals(command);
    }

    public static String normalizeCommand(String value) {
        return normalize(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
