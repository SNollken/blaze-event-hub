package com.nollen.blaze.points;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryPointsStore implements PointsStore {

    private final ConcurrentHashMap<String, PointsLedger> ledgerById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PointsRule> ruleByEventType = new ConcurrentHashMap<>();

    @Override
    public void saveLedger(PointsLedger ledger) {
        ledgerById.put(ledger.id(), ledger);
    }

    @Override
    public List<PointsLedger> findAllLedgers() {
        return List.copyOf(ledgerById.values());
    }

    @Override
    public List<PointsLedger> findByUserId(String userId) {
        List<PointsLedger> result = new ArrayList<>();
        for (PointsLedger entry : ledgerById.values()) {
            if (userId.equals(entry.userId())) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<PointsLedger> findLedgerBySourceEventId(String sourceEventId) {
        if (sourceEventId == null) {
            return Optional.empty();
        }
        for (PointsLedger entry : ledgerById.values()) {
            if (sourceEventId.equals(entry.sourceEventId())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    @Override
    public void saveRule(PointsRule rule) {
        ruleByEventType.put(rule.eventType(), rule);
    }

    @Override
    public List<PointsRule> findAllRules() {
        return List.copyOf(ruleByEventType.values());
    }

    @Override
    public Optional<PointsRule> findRuleByEventType(String eventType) {
        return Optional.ofNullable(ruleByEventType.get(eventType));
    }
}
