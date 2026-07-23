package com.blaze.eventhub.event;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blaze.eventhub.event.participant.EventParticipant;

/**
 * Serviço de regras de ação por evento.
 * Gerencia quais tipos de ação contam como entrada e com que peso.
 * Suporta sistema de tiers/bônus com modos REPLACE ou ACCUMULATE.
 */
@Service
public class EventActionRuleService {

    private final EventActionRuleStore ruleStore;
    private final EventActionTierStore tierStore;
    private final Clock clock;

    public EventActionRuleService(EventActionRuleStore ruleStore, EventActionTierStore tierStore, Clock clock) {
        this.ruleStore = ruleStore;
        this.tierStore = tierStore;
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
     * Retorna o modo de acumulação de tiers para um tipo de ação.
     */
    public TierMode mode(String eventId, ActionType actionType) {
        return ruleStore.findByEventIdAndType(eventId, actionType)
                .map(EventActionRule::mode)
                .orElse(TierMode.REPLACE);
    }

    // ========== TIER CRUD ==========

    /**
     * Lista todos os tiers de um evento.
     */
    public List<EventActionTier> listTiers(String eventId) {
        return tierStore.findByEventId(eventId);
    }

    /**
     * Lista os tiers de um evento para um tipo de ação específico.
     */
    public List<EventActionTier> listTiers(String eventId, ActionType actionType) {
        return tierStore.findByEventIdAndType(eventId, actionType).stream()
                .sorted(Comparator.comparingInt(EventActionTier::tierOrder))
                .toList();
    }

    /**
     * Salva os tiers para um evento e tipo de ação.
     */
    @Transactional
    public void replaceTiers(String eventId, ActionType actionType, List<EventActionTier> tiers) {
        tierStore.deleteByEventIdAndType(eventId, actionType);
        if (!tiers.isEmpty()) {
            tierStore.saveAll(tiers);
        }
    }

    /**
     * Calcula os entries baseados no tier applicable para a contagem de ações brutas.
     * @param actionType tipo de ação
     * @param rawCount contagem bruta de ações do usuário
     * @param tiers tiers configurados (já ordenados por tierOrder)
     * @param mode modo de acumulação (REPLACE ou ACCUMULATE)
     * @return entries calculados
     */
    public int calculateEntries(ActionType actionType, int rawCount, List<EventActionTier> tiers, TierMode mode) {
        if (tiers.isEmpty() || rawCount <= 0) {
            return 1; // padrão: 1 entry se não há tiers
        }

        if (mode == TierMode.ACCUMULATE) {
            // ACCUMULATE: soma todos os tiers atingidos
            int total = 0;
            for (EventActionTier tier : tiers) {
                if (rawCount >= tier.threshold()) {
                    total += tier.entries();
                }
            }
            return total > 0 ? total : 1;
        } else {
            // REPLACE: maior tier substitui os menores
            int highestEntries = 1;
            for (EventActionTier tier : tiers) {
                if (rawCount >= tier.threshold()) {
                    highestEntries = tier.entries();
                }
            }
            return highestEntries;
        }
    }

    /**
     * Salva as regras de ação para um evento.
     * Substitui todas as regras existentes.
     */
    @Transactional
    public void replaceRules(String eventId, List<ActionType> enabledTypes, Map<String, Integer> weights, Map<String, String> modes) {
        ruleStore.deleteByEventId(eventId);

        Instant now = Instant.now(clock);
        List<EventActionRule> rules = new ArrayList<>();
        for (ActionType type : ActionType.values()) {
            boolean enabled = enabledTypes.contains(type);
            int w = weights.getOrDefault(type.name(), 1);
            TierMode mode = TierMode.fromString(modes.getOrDefault(type.name(), "REPLACE"));
            rules.add(new EventActionRule(
                    UUID.randomUUID().toString(),
                    eventId,
                    type,
                    enabled,
                    w,
                    mode,
                    now));
        }
        ruleStore.saveAll(rules);
    }

    /**
     * Salva as regras de ação para um evento.
     * Substitui todas as regras existentes (compatibilidade com versão anterior).
     */
    @Transactional
    public void replaceRules(String eventId, List<ActionType> enabledTypes, Map<String, Integer> weights) {
        replaceRules(eventId, enabledTypes, weights, Map.of());
    }

    /**
     * Inicializa regras padrão para um evento novo.
     * Por padrão, apenas CHAT está habilitado.
     */
    public void initializeDefaults(String eventId) {
        replaceRules(eventId, List.of(ActionType.CHAT), Map.of());
    }
}
