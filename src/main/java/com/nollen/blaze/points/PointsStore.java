package com.nollen.blaze.points;

import java.util.List;
import java.util.Optional;

public interface PointsStore {

    void saveLedger(PointsLedger ledger);

    List<PointsLedger> findAllLedgers();

    List<PointsLedger> findByUserId(String userId);

    Optional<PointsLedger> findLedgerBySourceEventId(String sourceEventId);

    void saveRule(PointsRule rule);

    List<PointsRule> findAllRules();

    Optional<PointsRule> findRuleByEventType(String eventType);
}
