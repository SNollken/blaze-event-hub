package com.blaze.eventhub.event;

import com.blaze.eventhub.blaze.BlazeChannelResponse;
import com.blaze.eventhub.blaze.BlazeChannelService;
import com.blaze.eventhub.member.Member;
import com.blaze.eventhub.member.MemberService;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventControllerTest {

    @Test
    void createsWithTheAuthenticatedChannelWhenTheBrowserDoesNotSendASlug() {
        EventService eventService = mock(EventService.class);
        EventFinalizationService finalizationService = mock(EventFinalizationService.class);
        MemberService memberService = mock(MemberService.class);
        BlazeChannelService channelService = mock(BlazeChannelService.class);
        EventController controller = new EventController(
                eventService, finalizationService, memberService, channelService);
        Member member = new Member(
                "member-1", "user-1", "creator", "Creator", null, "active",
                Instant.EPOCH, Instant.EPOCH);
        BlazeChannelResponse channel = new BlazeChannelResponse(
                "channel-42", "creator", "Creator Live", null);
        CreateEventRequest request = new CreateEventRequest(
                "Community giveaway", "Description", "Gift card", "!join",
                null, null, null);
        EventResponse created = mock(EventResponse.class);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(channelService.resolveOwned(member)).thenReturn(channel);
        when(eventService.createEvent(request, member.id(), member.blazeUserId(), channel))
                .thenReturn(created);

        var response = controller.createEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
        verify(channelService).resolveOwned(member);
        verify(eventService).createEvent(request, member.id(), member.blazeUserId(), channel);
    }
}
