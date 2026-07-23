package com.blaze.eventhub.event;

import java.time.Instant;

/**
 * Regra de ação configurável por evento.
 * Define quais tipos de ação contam como entrada e com que peso.
 * Inclui modo de acumulação de tiers (REPLACE ou ACCUMULATE).
 */
public record EventActionRule(
        String id,
        String eventId,
        ActionType actionType,
        boolean enabled,
        int weight,
        TierMode mode,
        Instant createdAt
) {
}
