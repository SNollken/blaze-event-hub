package com.blaze.eventhub.event.participant;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class BlazeChatMessageParser {

    public List<ChatEntryCandidate> parse(String requestedChannelId, Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            throw new InvalidChatPayloadException("Blaze retornou um payload de chat vazio");
        }

        if (!(response.get("data") instanceof Map<?, ?> data)) {
            throw new InvalidChatPayloadException("Blaze retornou um payload de chat sem data valido");
        }
        if (!(data.get("messages") instanceof List<?> messages)) {
            throw new InvalidChatPayloadException("Blaze retornou um payload de chat sem messages valido");
        }
        String channelId = firstText(data, "channelId", "channel_id");
        if (channelId == null) {
            channelId = clean(requestedChannelId);
        }
        if (channelId == null) {
            throw new InvalidChatPayloadException("Nao foi possivel identificar o canal do payload de chat");
        }

        List<ChatEntryCandidate> candidates = new ArrayList<>();
        for (Object item : messages) {
            if (!(item instanceof Map<?, ?> message)) {
                continue;
            }
            ChatEntryCandidate candidate = toCandidate(channelId, message);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private static ChatEntryCandidate toCandidate(String channelId, Map<?, ?> message) {
        String messageId = firstText(message, "id", "messageId", "message_id");
        String content = firstText(message, "message", "content", "text");
        Map<?, ?> sender = message.get("sender") instanceof Map<?, ?> nested ? nested : Map.of();
        String blazeUserId = firstText(sender, "id", "userId", "user_id", "blazeUserId", "blaze_user_id");
        if (blazeUserId == null) {
            blazeUserId = firstText(message, "senderId", "sender_id", "userId", "user_id", "blazeUserId", "blaze_user_id");
        }
        if (messageId == null || content == null || blazeUserId == null) {
            return null;
        }

        String username = firstText(sender, "username", "handle");
        if (username == null) {
            username = firstText(message, "username");
        }
        String displayName = firstText(sender, "displayName", "display_name", "name");
        if (displayName == null) {
            displayName = firstText(message, "displayName", "display_name");
        }

        Instant sentAt = parseInstant(firstValue(message,
                "createdAt", "created_at", "sentAt", "sent_at", "timestamp"));
        if (sentAt == null) {
            return null;
        }

        return new ChatEntryCandidate(
                channelId,
                messageId,
                content,
                blazeUserId,
                username,
                displayName,
                sentAt);
    }

    private static Instant parseInstant(Object value) {
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 10_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        String text = value == null ? null : clean(value.toString());
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException invalidTimestamp) {
            return null;
        }
    }

    private static String firstText(Map<?, ?> values, String... keys) {
        Object value = firstValue(values, keys);
        return value == null ? null : clean(value.toString());
    }

    private static Object firstValue(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && clean(value.toString()) != null) {
                return value;
            }
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
