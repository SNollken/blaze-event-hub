package com.blaze.eventhub.event;

import java.util.List;
import java.util.Map;

/**
 * Request para atualizar as regras de ação de um evento.
 */
public record UpdateActionRulesRequest(
        List<String> actionTypes,
        Map<String, Integer> weights
) {
    public UpdateActionRulesRequest {
        if (weights == null) weights = Map.of();
    }
}
