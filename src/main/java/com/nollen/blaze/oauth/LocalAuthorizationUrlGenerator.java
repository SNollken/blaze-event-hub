package com.nollen.blaze.oauth;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Gera localmente a authorizationUrl do OAuth com PKCE S256,
 * sem depender de chamada externa para a Blaze.
 *
 * state e codeVerifier são gerados com SecureRandom.
 * codeChallenge é derivado do codeVerifier via SHA-256 (S256).
 */
@Component
public class LocalAuthorizationUrlGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STATE_BYTES = 32;
    private static final int VERIFIER_BYTES = 32;

    /**
     * Gera state, codeVerifier, codeChallenge e monta a authorizationUrl.
     */
    public GeneratedAuthUrl generate(String clientId, String redirectUri, List<String> scopes) {
        String state = generateRandomBase64(STATE_BYTES);
        String codeVerifier = generateRandomBase64(VERIFIER_BYTES);
        String codeChallenge = s256CodeChallenge(codeVerifier);
        String authorizationUrl = buildAuthorizationUrl(clientId, redirectUri, scopes, state, codeChallenge);
        return new GeneratedAuthUrl(authorizationUrl, state, codeVerifier);
    }

    private static String generateRandomBase64(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String s256CodeChallenge(String codeVerifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String buildAuthorizationUrl(String clientId, String redirectUri, List<String> scopes,
                                         String state, String codeChallenge) {
        String scopeParam = scopes == null || scopes.isEmpty()
                ? ""
                : scopes.stream().map(LocalAuthorizationUrlGenerator::encodeParam)
                        .collect(Collectors.joining(" "));

        return "https://blaze.stream/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=" + encodeParam(clientId)
                + "&redirect_uri=" + encodeParam(redirectUri)
                + "&scope=" + encodeParam(scopeParam)
                + "&state=" + encodeParam(state)
                + "&code_challenge_method=S256"
                + "&code_challenge=" + encodeParam(codeChallenge);
    }

    static String encodeParam(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not available", e);
        }
    }
}
