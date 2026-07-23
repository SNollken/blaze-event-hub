package com.blaze.eventhub.event;

import java.time.Instant;

/**
 * Regra de ação configurável por evento.
 * Define quais tipos de ação contam como entrada e com que peso.
 */
public record EventActionRule(
        String id,
        String eventId,
        ActionType actionType,
        boolean enabled,
        int weight,
        Instant createdAt
) {
}
