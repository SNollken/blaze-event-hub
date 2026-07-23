package com.blaze.eventhub.event;

import java.time.Instant;

/**
 * Tier de bônus para um tipo de ação em um evento.
 * Define threshold (ex: 10 votos), entries ganhos (ex: 1 entrada) e ordem do tier.
 */
public record EventActionTier(
        String id,
        String eventId,
        ActionType actionType,
        int threshold,
        int entries,
        int tierOrder,
        Instant createdAt
) {
}