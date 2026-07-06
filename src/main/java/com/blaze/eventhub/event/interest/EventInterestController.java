package com.blaze.eventhub.event.interest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blaze.eventhub.member.Member;
import com.blaze.eventhub.member.MemberService;

@RestController
@RequestMapping("/api/events/{eventId}/interest")
public class EventInterestController {

    private final EventInterestService interestService;
    private final MemberService memberService;

    public EventInterestController(EventInterestService interestService, MemberService memberService) {
        this.interestService = interestService;
        this.memberService = memberService;
    }

    @PostMapping
    ResponseEntity<EventInterest> expressInterest(@PathVariable String eventId) {
        Member member = memberService.getCurrentMember();
        EventInterest interest = interestService.expressInterest(eventId, member.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(interest);
    }

    @DeleteMapping
    EventInterest withdrawInterest(@PathVariable String eventId) {
        Member member = memberService.getCurrentMember();
        return interestService.withdrawInterest(eventId, member.id());
    }

    @GetMapping("/participants")
    List<EventInterestResponse> getParticipants(@PathVariable String eventId) {
        return interestService.getParticipants(eventId);
    }
}
