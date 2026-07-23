package com.blaze.eventhub.event;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de regras de ação por evento.
 * Gerencia quais tipos de ação contam como entrada e com que peso.
 */
@Service
public class EventActionRuleService {

    private final EventActionRuleStore ruleStore;
    private final Clock clock;

    public EventActionRuleService(EventActionRuleStore ruleStore, Clock clock) {
        this.ruleStore = ruleStore;
        this.clock = clock;
    }

    /**
     * Retorna as regras ativas para um evento.
     */
    public List<EventActionRule> listByEventId(String eventId) {
        return ruleStore.findByEventId(eventId);
    }

    /**
     * Retorna os tipos de ação habilitados para um evento.
     */
    public List<ActionType> enabledTypes(String eventId) {
        return ruleStore.findByEventId(eventId).stream()
                .filter(EventActionRule::enabled)
                .map(EventActionRule::actionType)
                .toList();
    }

    /**
     * Verifica se um tipo de ação está habilitado para o evento.
     */
    public boolean isEnabled(String eventId, ActionType actionType) {
        return ruleStore.findByEventIdAndType(eventId, actionType)
                .map(EventActionRule::enabled)
                .orElse(false);
    }

    /**
     * Retorna o peso de uma ação para o evento. Peso padrão = 1.
     */
    public int weight(String eventId, ActionType actionType) {
        return ruleStore.findByEventIdAndType(eventId, actionType)
                .map(EventActionRule::weight)
                .orElse(1);
    }

    /**
     * Salva as regras de ação para um evento.
     * Substitui todas as regras existentes.
     */
    @Transactional
    public void replaceRules(String eventId, List<ActionType> enabledTypes, Map<String, Integer> weights) {
        ruleStore.deleteByEventId(eventId);

        Instant now = Instant.now(clock);
        List<EventActionRule> rules = new ArrayList<>();
        for (ActionType type : ActionType.values()) {
            boolean enabled = enabledTypes.contains(type);
            int w = weights.getOrDefault(type.name(), 1);
            rules.add(new EventActionRule(
                    UUID.randomUUID().toString(),
                    eventId,
                    type,
                    enabled,
                    w,
                    now));
        }
        ruleStore.saveAll(rules);
    }

    /**
     * Inicializa regras padrão para um evento novo.
     * Por padrão, apenas CHAT está habilitado.
     */
    public void initializeDefaults(String eventId) {
        replaceRules(eventId, List.of(ActionType.CHAT), Map.of());
    }
}
