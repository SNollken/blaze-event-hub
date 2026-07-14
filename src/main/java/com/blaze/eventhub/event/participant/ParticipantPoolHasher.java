package com.blaze.eventhub.event.participant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class ParticipantPoolHasher {

    private ParticipantPoolHasher() {
    }

    public static String sha256(List<EventParticipant> participants) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            participants.stream()
                    .map(EventParticipant::blazeUserId)
                    .sorted()
                    .forEach(userId -> updateLengthPrefixed(digest, userId));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(valueBytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(valueBytes);
        digest.update((byte) '\n');
    }
}
