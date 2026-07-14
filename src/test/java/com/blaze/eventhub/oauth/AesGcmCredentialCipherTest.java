package com.blaze.eventhub.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.blaze.eventhub.common.ConfigurationMissingException;

class AesGcmCredentialCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomNonceAndDecryptsAuthenticatedCiphertext() {
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(KEY);

        String first = cipher.encrypt("sensitive-access-token", "member-1:access");
        String second = cipher.encrypt("sensitive-access-token", "member-1:access");

        assertNotEquals("sensitive-access-token", first);
        assertNotEquals(first, second);
        assertEquals("sensitive-access-token", cipher.decrypt(first, "member-1:access"));
        assertEquals("sensitive-access-token", cipher.decrypt(second, "member-1:access"));
        assertThrows(IllegalArgumentException.class,
                () -> cipher.decrypt(first, "member-2:access"));
    }

    @Test
    void rejectsTamperingAndMissingExternalKey() {
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(KEY);
        String encrypted = cipher.encrypt("token", "member-1:refresh");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThrows(IllegalArgumentException.class,
                () -> cipher.decrypt(tampered, "member-1:refresh"));
        assertThrows(ConfigurationMissingException.class,
                () -> new AesGcmCredentialCipher("").encrypt("token", "member-1:access"));
    }
}
