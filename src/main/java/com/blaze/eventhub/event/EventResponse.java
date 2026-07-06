package com.blaze.eventhub.event;

import java.time.Instant;
import java.util.List;

public record EventResponse(
        String id,
        String creatorMemberId,
        String creatorBlazeUserId,
        String creatorChannelId,
        String title,
        String description,
        String prizeType,
        String prizeDescription,
        String status,
        String rulesMode,
        int maxEntriesPerParticipant,
        boolean requiresInterestBeforeAction,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        Instant completedAt,
        List<EventRuleResponse> rules) {

    public static EventResponse from(Event event, List<EventRule> rules) {
        List<EventRuleResponse> ruleResponses = rules.stream()
                .map(EventRuleResponse::from)
                .toList();

        return new EventResponse(
                event.id(),
                event.creatorMemberId(),
                event.creatorBlazeUserId(),
                event.creatorChannelId(),
                event.title(),
                event.description(),
                event.prizeType(),
                event.prizeDescription(),
                event.status().name().toLowerCase(),
                event.rulesMode().name().toLowerCase(),
                event.maxEntriesPerParticipant(),
                event.requiresInterestBeforeAction(),
                event.startsAt(),
                event.endsAt(),
                event.createdAt(),
                event.updatedAt(),
                event.closedAt(),
                event.completedAt(),
                ruleResponses);
    }
}
