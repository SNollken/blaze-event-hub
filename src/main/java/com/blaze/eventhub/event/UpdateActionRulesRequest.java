package com.blaze.eventhub.event;

import java.util.List;

/**
 * Request para atualizar as regras de ação de um evento.
 */
public record UpdateActionRulesRequest(
        List<String> actionTypes
) {
}
