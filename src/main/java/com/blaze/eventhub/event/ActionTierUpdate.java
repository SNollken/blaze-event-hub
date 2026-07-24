package com.blaze.eventhub.event;

import java.util.List;

/**
 * Request para atualizar tiers de um tipo de ação.
 */
public record ActionTierUpdate(
        String actionType,
        List<TierUpdate> tiers
) {
    public record TierUpdate(
            int threshold,
            int entries,
            int tierOrder
    ) {}
}