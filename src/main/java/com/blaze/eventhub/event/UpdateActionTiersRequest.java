package com.blaze.eventhub.event;

import java.util.List;

public record UpdateActionTiersRequest(
        List<ActionTierUpdate> tiers
) {
    public record ActionTierUpdate(
            String actionType,
            List<TierUpdate> tiers
    ) {}

    public record TierUpdate(
            int threshold,
            int entries,
            int tierOrder
    ) {}
}