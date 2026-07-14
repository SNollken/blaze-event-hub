package com.blaze.eventhub.blaze;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.member.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlazeChannelServiceTest {

    private BlazeApiClient apiClient;
    private BlazeChannelService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(BlazeApiClient.class);
        service = new BlazeChannelService(apiClient);
    }

    @Test
    void resolvesCanonicalChannelIdentity() {
        when(apiClient.getChannelsBySlug("creator")).thenReturn(channelResponse("creator"));

        BlazeChannelResponse channel = service.resolve(" @Creator ");

        assertThat(channel.id()).isEqualTo("channel-1");
        assertThat(channel.slug()).isEqualTo("creator");
        assertThat(channel.displayName()).isEqualTo("Creator Live");
        assertThat(channel.avatarUrl()).isEqualTo("https://cdn.example/avatar.png");
    }

    @Test
    void onlyAllowsTheConnectedCreatorsOwnChannel() {
        when(apiClient.getChannelsBySlug("other-channel"))
                .thenReturn(channelResponse("other-channel"));
        Member member = new Member(
                "member-1", "user-1", "creator", "Creator", null, "active",
                Instant.EPOCH, Instant.EPOCH);

        assertThatThrownBy(() -> service.resolveOwned("other-channel", member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("conta Blaze conectada");
    }

    @Test
    void provesChannelOwnershipWithTheAuthenticatedBlazeUserId() {
        when(apiClient.getChannelsBySlug("creator"))
                .thenReturn(channelResponse("user-1", "creator"));
        Member member = new Member(
                "member-1", "user-1", "creator", "Creator", null, "active",
                Instant.EPOCH, Instant.EPOCH);

        BlazeChannelResponse channel = service.resolveOwned("creator", member);

        assertThat(channel.id()).isEqualTo(member.blazeUserId());
    }

    @Test
    void rejectsMalformedBlazeResponse() {
        when(apiClient.getChannelsBySlug("creator")).thenReturn(Map.of("data", Map.of()));

        assertThatThrownBy(() -> service.resolve("creator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao encontrado");
    }

    private static Map<String, Object> channelResponse(String slug) {
        return channelResponse("channel-1", slug);
    }

    private static Map<String, Object> channelResponse(String channelId, String slug) {
        return Map.of("data", Map.of("rows", List.of(Map.of(
                "id", channelId,
                "slug", slug,
                "displayName", "Creator Live",
                "avatarUrl", "https://cdn.example/avatar.png"))));
    }
}
