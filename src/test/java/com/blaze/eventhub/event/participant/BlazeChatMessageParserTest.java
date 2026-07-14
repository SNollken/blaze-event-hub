package com.blaze.eventhub.event.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BlazeChatMessageParserTest {

    private final BlazeChatMessageParser parser = new BlazeChatMessageParser();

    @Test
    void parsesNestedDataMessagesAndSupportedFallbacks() {
        Map<String, Object> response = Map.of(
                "data", Map.of(
                        "channelId", "channel-from-response",
                        "messages", List.of(
                                Map.of(
                                        "id", "message-1",
                                        "message", "!participar",
                                        "createdAt", "2026-07-14T12:00:00Z",
                                        "sender", Map.of(
                                                "id", "user-1",
                                                "username", "viewer_one",
                                                "displayName", "Viewer One")),
                                Map.of(
                                        "id", "message-2",
                                        "content", "!participar",
                                        "senderId", "user-2"),
                                Map.of(
                                        "id", "message-without-sender",
                                        "message", "!participar"))));

        List<ChatEntryCandidate> candidates = parser.parse("requested-channel", response);

        assertEquals(1, candidates.size());
        ChatEntryCandidate first = candidates.get(0);
        assertEquals("channel-from-response", first.channelId());
        assertEquals("message-1", first.messageId());
        assertEquals("!participar", first.message());
        assertEquals("user-1", first.blazeUserId());
        assertEquals("viewer_one", first.blazeUsername());
        assertEquals("Viewer One", first.displayName());
        assertEquals(Instant.parse("2026-07-14T12:00:00Z"), first.sentAt());

    }

    @Test
    void fallsBackToRequestedChannelWhenPayloadIsValid() {
        Map<String, Object> response = Map.of(
                "data", Map.of(
                        "messages", List.of(Map.of(
                                "id", "message-1",
                                "message", "hello",
                                "createdAt", "2026-07-14T12:00:00Z",
                                "userId", "user-1"))));

        List<ChatEntryCandidate> candidates = parser.parse("requested-channel", response);

        assertEquals(1, candidates.size());
        assertEquals("requested-channel", candidates.getFirst().channelId());
    }

    @Test
    void acceptsAValidEmptyMessagesList() {
        Map<String, Object> response = Map.of(
                "data", Map.of(
                        "channelId", "channel-1",
                        "messages", List.of()));

        assertEquals(List.of(), parser.parse("requested-channel", response));
    }

    @Test
    void rejectsNullOrEmptyPayload() {
        assertThrows(InvalidChatPayloadException.class,
                () -> parser.parse("requested-channel", null));
        assertThrows(InvalidChatPayloadException.class,
                () -> parser.parse("requested-channel", Map.of()));
    }

    @Test
    void rejectsPayloadWithoutDataMessagesStructure() {
        assertThrows(InvalidChatPayloadException.class,
                () -> parser.parse("requested-channel", Map.of("data", "invalid")));
        assertThrows(InvalidChatPayloadException.class,
                () -> parser.parse("requested-channel", Map.of("data", Map.of())));
        assertThrows(InvalidChatPayloadException.class,
                () -> parser.parse("requested-channel", Map.of(
                        "data", Map.of("messages", "invalid"))));
    }

    @Test
    void ignoresMessagesWithoutAValidTimestamp() {
        Map<String, Object> response = Map.of("data", Map.of(
                "messages", List.of(
                        Map.of("id", "missing", "message", "!participar", "userId", "u1"),
                        Map.of("id", "invalid", "message", "!participar", "userId", "u2",
                                "createdAt", "not-a-date"))));

        assertEquals(List.of(), parser.parse("channel-1", response));
    }
}
