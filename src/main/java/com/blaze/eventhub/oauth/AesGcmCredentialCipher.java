package com.blaze.eventhub.oauth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.blaze.eventhub.common.ConfigurationMissingException;

@Component
public class AesGcmCredentialCipher {

    private static final String VERSION_PREFIX = "v2.";
    private static final String AAD_PREFIX = "blaze-event-hub:oauth:v2:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String configuredKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCredentialCipher(
            @Value("${eventhub.security.credential-encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    public void validateConfiguration() {
        requireKey();
    }

    public String encrypt(String plaintext, String context) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, nonce, context);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array();
            return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Nao foi possivel criptografar a credencial OAuth", failure);
        }
    }

    public String decrypt(String encodedCiphertext, String context) {
        if (encodedCiphertext == null) {
            return null;
        }
        if (!encodedCiphertext.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("Versao de credencial criptografada invalida");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encodedCiphertext.substring(VERSION_PREFIX.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Credencial criptografada truncada");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[payload.length - NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, nonce.length);
            System.arraycopy(payload, nonce.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = newCipher(Cipher.DECRYPT_MODE, nonce, context);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("Credencial OAuth invalida ou adulterada", failure);
        }
    }

    private Cipher newCipher(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("Contexto da credencial OAuth e obrigatorio");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(requireKey(), "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD((AAD_PREFIX + context).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private byte[] requireKey() {
        if (configuredKey.isBlank()) {
            throw new ConfigurationMissingException(
                    "EVENTHUB_CREDENTIAL_ENCRYPTION_KEY e obrigatoria para persistir OAuth");
        }
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException invalidBase64) {
            throw new ConfigurationMissingException(
                    "EVENTHUB_CREDENTIAL_ENCRYPTION_KEY deve ser Base64 valido de 32 bytes");
        }
        if (key.length != 32) {
            throw new ConfigurationMissingException(
                    "EVENTHUB_CREDENTIAL_ENCRYPTION_KEY deve conter exatamente 32 bytes em Base64");
        }
        return key;
    }
}
