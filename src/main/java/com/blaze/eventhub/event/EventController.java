package com.blaze.eventhub.event;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blaze.eventhub.member.MemberService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final MemberService memberService;

    public EventController(EventService eventService, MemberService memberService) {
        this.eventService = eventService;
        this.memberService = memberService;
    }

    @GetMapping
    List<EventResponse> listEvents(@RequestParam(required = false) String status) {
        return eventService.listEvents(status);
    }

    @PostMapping
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        var member = memberService.getCurrentMember();
        EventResponse response = eventService.createEvent(request, member.id(), member.blazeUserId(),
                request.creatorChannelId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my/history")
    Map<String, List<EventResponse>> myHistory() {
        var member = memberService.getCurrentMember();
        return eventService.getCreatorHistory(member.id());
    }

    @GetMapping("/{id}")
    EventResponse getEvent(@PathVariable String id) {
        return eventService.getEvent(id);
    }

    @GetMapping("/{id}/stats")
    Map<String, Object> getStats(@PathVariable String id) {
        return eventService.getEventStats(id);
    }

    @PutMapping("/{id}")
    EventResponse updateEvent(@PathVariable String id,
            @Valid @RequestBody UpdateEventRequest request) {
        var member = memberService.getCurrentMember();
        return eventService.updateEvent(id, request, member.id());
    }

    @PostMapping("/{id}/open")
    EventResponse openEvent(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        return eventService.openEvent(id, member.id());
    }

    @PostMapping("/{id}/close")
    EventResponse closeEvent(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        return eventService.closeEvent(id, member.id());
    }

    @PostMapping("/{id}/cancel")
    EventResponse cancelEvent(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        return eventService.cancelEvent(id, member.id());
    }

    @PostMapping("/{id}/rules")
    ResponseEntity<EventRuleResponse> addRule(@PathVariable String id,
            @Valid @RequestBody CreateEventRuleRequest request) {
        var member = memberService.getCurrentMember();
        EventRuleResponse response = eventService.addRule(id, request, member.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/rules/{ruleId}")
    EventRuleResponse updateRule(@PathVariable String id,
            @PathVariable String ruleId,
            @Valid @RequestBody UpdateEventRuleRequest request) {
        var member = memberService.getCurrentMember();
        return eventService.updateRule(id, ruleId, request, member.id());
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    ResponseEntity<Void> removeRule(@PathVariable String id,
            @PathVariable String ruleId) {
        var member = memberService.getCurrentMember();
        eventService.removeRule(id, ruleId, member.id());
        return ResponseEntity.noContent().build();
    }
}
