package com.blaze.eventhub.blaze;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.member.Member;
import com.blaze.eventhub.oauth.PersistentOAuthCredentialService;
import com.blaze.eventhub.oauth.TokenSnapshot;

@Service
public class BlazeChannelService {

    private final BlazeApiClient apiClient;
    private final PersistentOAuthCredentialService credentialService;

    public BlazeChannelService(
            BlazeApiClient apiClient,
            PersistentOAuthCredentialService credentialService) {
        this.apiClient = apiClient;
        this.credentialService = credentialService;
    }

    public BlazeChannelResponse resolve(String requestedSlug) {
        String slug = normalizeSlug(requestedSlug);
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Informe o canal Blaze.");
        }

        return parseChannel(slug, apiClient.getChannelsBySlug(slug));
    }

    private BlazeChannelResponse resolve(String requestedSlug, TokenSnapshot token) {
        String slug = normalizeSlug(requestedSlug);
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Informe o canal Blaze.");
        }

        return parseChannel(slug, apiClient.getChannelsBySlug(slug, token));
    }

    private BlazeChannelResponse parseChannel(String slug, Map<String, Object> response) {
        Map<?, ?> data = response == null ? null : asMap(response.get("data"));
        List<?> rows = data == null ? null : asList(data.get("rows"));
        Map<?, ?> channel = rows == null || rows.isEmpty() ? null : asMap(rows.getFirst());
        if (channel == null) {
            throw new IllegalArgumentException("Canal Blaze nao encontrado.");
        }

        String id = text(channel.get("id"));
        String canonicalSlug = normalizeSlug(text(channel.get("slug")));
        if (id.isBlank() || canonicalSlug.isBlank() || !canonicalSlug.equals(slug)) {
            throw new IllegalArgumentException("A Blaze retornou um canal invalido para o slug informado.");
        }

        String displayName = text(channel.get("displayName"));
        String avatarUrl = safeUrl(text(channel.get("avatarUrl")));
        return new BlazeChannelResponse(
                id,
                canonicalSlug,
                displayName.isBlank() ? canonicalSlug : displayName,
                avatarUrl);
    }

    public BlazeChannelResponse resolveOwned(Member member) {
        String memberId = member == null ? "" : text(member.id());
        String authenticatedChannelSlug = member == null ? "" : normalizeSlug(member.blazeUsername());
        if (memberId.isBlank() || authenticatedChannelSlug.isBlank()) {
            throw new ForbiddenException("A conta Blaze conectada nao informou um canal valido.");
        }

        TokenSnapshot token = credentialService.currentValid(memberId);
        try {
            return resolve(authenticatedChannelSlug, token);
        }
        catch (IllegalArgumentException ex) {
            throw new ForbiddenException(
                    "Nao foi possivel vincular o canal da conta Blaze conectada.");
        }
    }

    private static String normalizeSlug(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return clean.startsWith("@") ? clean.substring(1) : clean;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String safeUrl(String value) {
        return value.startsWith("https://") || value.startsWith("http://") ? value : null;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : null;
    }
}
