package com.blaze.eventhub.event;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blaze.eventhub.member.MemberService;
import com.blaze.eventhub.blaze.BlazeChannelService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventFinalizationService finalizationService;
    private final MemberService memberService;
    private final BlazeChannelService channelService;
    private final EventActionRuleService actionRuleService;

    public EventController(
            EventService eventService,
            EventFinalizationService finalizationService,
            MemberService memberService,
            BlazeChannelService channelService,
            EventActionRuleService actionRuleService) {
        this.eventService = eventService;
        this.finalizationService = finalizationService;
        this.memberService = memberService;
        this.channelService = channelService;
        this.actionRuleService = actionRuleService;
    }

    @GetMapping
    List<EventResponse> listEvents(@RequestParam(required = false) String status) {
        return eventService.listEvents(status);
    }

    @PostMapping
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        var member = memberService.getCurrentMember();
        var channel = channelService.resolveOwned(member);
        EventResponse response = eventService.createEvent(request, member.id(), member.blazeUserId(),
                channel);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my/history")
    Map<String, List<EventResponse>> myHistory() {
        var member = memberService.getCurrentMember();
        return eventService.getCreatorHistory(member.id());
    }

    @GetMapping("/{id}")
    EventResponse getEvent(@PathVariable String id) {
        return eventService.getVisibleEvent(id, currentMemberIdOrNull());
    }

    @GetMapping("/{id}/stats")
    EventLifecycleStats getStats(@PathVariable String id) {
        return eventService.getVisibleEventStats(id, currentMemberIdOrNull());
    }

    @GetMapping("/{id}/participants")
    List<EventParticipantResponse> getParticipants(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        return eventService.getParticipants(id, member.id());
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

    @PostMapping("/{id}/finalize")
    EventResponse finalizeEvent(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        return finalizationService.finalizeEvent(id, member.id());
    }

    @PostMapping("/{id}/cancel")
    EventResponse cancelEvent(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        return eventService.cancelEvent(id, member.id());
    }

    @GetMapping("/{id}/action-rules")
    List<EventActionRuleResponse> getActionRules(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        eventService.getParticipants(id, member.id());
        return EventActionRuleResponse.fromList(actionRuleService.listByEventId(id));
    }

    @GetMapping("/{id}/action-tiers")
    List<EventActionTierResponse> getActionTiers(@PathVariable String id) {
        var member = memberService.getCurrentMember();
        eventService.getParticipants(id, member.id());
        return EventActionTierResponse.fromList(actionRuleService.listTiers(id));
    }

    @PutMapping("/{id}/action-rules")
    List<EventActionRuleResponse> updateActionRules(
            @PathVariable String id,
            @Valid @RequestBody UpdateActionRulesRequest request) {
        var member = memberService.getCurrentMember();
        eventService.getParticipants(id, member.id());
        List<ActionType> types = request.actionTypes().stream()
                .map(ActionType::fromString)
                .toList();
        actionRuleService.replaceRules(id, types, request.weights(), request.modes());
        return EventActionRuleResponse.fromList(actionRuleService.listByEventId(id));
    }

    @PutMapping("/{id}/action-tiers")
    List<EventActionTierResponse> updateActionTiers(
            @PathVariable String id,
            @Valid @RequestBody UpdateActionTiersRequest request) {
        var member = memberService.getCurrentMember();
        eventService.getParticipants(id, member.id());

        for (UpdateActionTiersRequest.ActionTierUpdate tierUpdate : request.tiers()) {
            ActionType actionType = ActionType.fromString(tierUpdate.actionType());
            List<EventActionTier> tiers = tierUpdate.tiers().stream()
                    .map(t -> new EventActionTier(
                            java.util.UUID.randomUUID().toString(),
                            id,
                            actionType,
                            t.threshold(),
                            t.entries(),
                            t.tierOrder(),
                            java.time.Instant.now()))
                    .toList();
            actionRuleService.replaceTiers(id, actionType, tiers);
        }

        return EventActionTierResponse.fromList(actionRuleService.listTiers(id));
    }

    private String currentMemberIdOrNull() {
        return memberService.findCurrentMember()
                .map(member -> member.id())
                .orElse(null);
    }
}