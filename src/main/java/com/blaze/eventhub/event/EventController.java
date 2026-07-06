package com.blaze.eventhub.event;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    List<EventResponse> listEvents(@RequestParam(required = false) String status) {
        return eventService.listEvents(status);
    }

    @PostMapping
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
            @RequestParam String creatorMemberId,
            @RequestParam String creatorBlazeUserId,
            @RequestParam String creatorChannelId) {
        EventResponse response = eventService.createEvent(request, creatorMemberId, creatorBlazeUserId, creatorChannelId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    EventResponse getEvent(@PathVariable String id) {
        return eventService.getEvent(id);
    }

    @PatchMapping("/{id}")
    EventResponse updateEvent(@PathVariable String id,
            @Valid @RequestBody UpdateEventRequest request,
            @RequestParam String memberId) {
        return eventService.updateEvent(id, request, memberId);
    }

    @PostMapping("/{id}/open")
    EventResponse openEvent(@PathVariable String id, @RequestParam String memberId) {
        return eventService.openEvent(id, memberId);
    }

    @PostMapping("/{id}/close")
    EventResponse closeEvent(@PathVariable String id, @RequestParam String memberId) {
        return eventService.closeEvent(id, memberId);
    }

    @PostMapping("/{id}/cancel")
    EventResponse cancelEvent(@PathVariable String id, @RequestParam String memberId) {
        return eventService.cancelEvent(id, memberId);
    }

    @PostMapping("/{id}/rules")
    ResponseEntity<EventRuleResponse> addRule(@PathVariable String id,
            @Valid @RequestBody CreateEventRuleRequest request,
            @RequestParam String memberId) {
        EventRuleResponse response = eventService.addRule(id, request, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/rules/{ruleId}")
    EventRuleResponse updateRule(@PathVariable String id,
            @PathVariable String ruleId,
            @Valid @RequestBody UpdateEventRuleRequest request,
            @RequestParam String memberId) {
        return eventService.updateRule(id, ruleId, request, memberId);
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    ResponseEntity<Void> removeRule(@PathVariable String id,
            @PathVariable String ruleId,
            @RequestParam String memberId) {
        eventService.removeRule(id, ruleId, memberId);
        return ResponseEntity.noContent().build();
    }
}
