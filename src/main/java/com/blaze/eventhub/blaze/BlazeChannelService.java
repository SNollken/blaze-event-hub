package com.blaze.eventhub.blaze;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.member.Member;

@Service
public class BlazeChannelService {

    private final BlazeApiClient apiClient;

    public BlazeChannelService(BlazeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public BlazeChannelResponse resolve(String requestedSlug) {
        String slug = normalizeSlug(requestedSlug);
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Informe o canal Blaze.");
        }

        Map<String, Object> response = apiClient.getChannelsBySlug(slug);
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

    public BlazeChannelResponse resolveOwned(String requestedSlug, Member member) {
        BlazeChannelResponse channel = resolve(requestedSlug);
        String authenticatedBlazeUserId = member == null ? "" : text(member.blazeUserId());
        if (authenticatedBlazeUserId.isBlank() || !channel.id().equals(authenticatedBlazeUserId)) {
            throw new ForbiddenException(
                    "Neste MVP, crie giveaways apenas no canal da conta Blaze conectada.");
        }
        return channel;
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
