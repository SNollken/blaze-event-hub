package com.blaze.eventhub.blaze;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.member.Member;
import com.blaze.eventhub.oauth.PersistentOAuthCredentialService;
import com.blaze.eventhub.oauth.TokenSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlazeChannelServiceTest {

    private BlazeApiClient apiClient;
    private PersistentOAuthCredentialService credentialService;
    private BlazeChannelService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(BlazeApiClient.class);
        credentialService = mock(PersistentOAuthCredentialService.class);
        service = new BlazeChannelService(apiClient, credentialService);
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
    void derivesTheChannelFromTheConnectedAccountAndAcceptsDifferentUserAndChannelIds() {
        Member member = new Member(
                "member-1", "user-1", "creator", "Creator", null, "active",
                Instant.EPOCH, Instant.EPOCH);
        TokenSnapshot token = new TokenSnapshot(
                "user", "user-1", "Bearer", "access", "refresh",
                Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now());
        when(credentialService.currentValid(member.id())).thenReturn(token);
        when(apiClient.getChannelsBySlug(member.blazeUsername(), token))
                .thenReturn(channelResponse("channel-1", "creator"));

        BlazeChannelResponse channel = service.resolveOwned(member);

        assertThat(channel.id()).isEqualTo("channel-1");
        assertThat(channel.id()).isNotEqualTo(member.blazeUserId());
        assertThat(channel.slug()).isEqualTo(member.blazeUsername());
        verify(credentialService).currentValid(member.id());
        verify(apiClient).getChannelsBySlug(member.blazeUsername(), token);
    }

    @Test
    void rejectsConnectedAccountWithoutAChannelSlug() {
        Member member = new Member(
                "member-1", "user-1", " ", "Creator", null, "active",
                Instant.EPOCH, Instant.EPOCH);

        assertThatThrownBy(() -> service.resolveOwned(member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("canal");
    }

    @Test
    void reportsAConnectionProblemWhenTheAuthenticatedChannelCannotBeResolved() {
        Member member = new Member(
                "member-1", "user-1", "creator", "Creator", null, "active",
                Instant.EPOCH, Instant.EPOCH);
        TokenSnapshot token = new TokenSnapshot(
                "user", "user-1", "Bearer", "access", "refresh",
                Instant.now().plusSeconds(3600), List.of("users.read"), Instant.now());
        when(credentialService.currentValid(member.id())).thenReturn(token);
        when(apiClient.getChannelsBySlug(member.blazeUsername(), token))
                .thenReturn(Map.of("data", Map.of("rows", List.of())));

        assertThatThrownBy(() -> service.resolveOwned(member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("vincular o canal");
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
