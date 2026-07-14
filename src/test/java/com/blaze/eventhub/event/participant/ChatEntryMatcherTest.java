package com.blaze.eventhub.event.participant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ChatEntryMatcherTest {

    @Test
    void matchesConfiguredCommandIgnoringCaseAndOuterWhitespace() {
        assertTrue(ChatEntryMatcher.matches("!Participar", "   !PARTICIPAR  "));
    }

    @Test
    void rejectsCommandEmbeddedInAnotherMessage() {
        assertFalse(ChatEntryMatcher.matches("!participar", "quero !participar agora"));
    }

    @Test
    void rejectsBlankCommandAndBlankMessage() {
        assertFalse(ChatEntryMatcher.matches("   ", "  "));
    }
}
