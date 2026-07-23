package com.blaze.eventhub.event;

import java.time.Instant;
import java.util.List;

public record EventActionRuleResponse(
        String id,
        String eventId,
        String actionType,
        boolean enabled,
        int weight,
        String mode,
        Instant createdAt) {

    public static EventActionRuleResponse from(EventActionRule rule) {
        return new EventActionRuleResponse(
                rule.id(),
                rule.eventId(),
                rule.actionType().value(),
                rule.enabled(),
                rule.weight(),
                rule.mode().value(),
                rule.createdAt());
    }

    public static List<EventActionRuleResponse> fromList(List<EventActionRule> rules) {
        return rules.stream().map(EventActionRuleResponse::from).toList();
    }
}
