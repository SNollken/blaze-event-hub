package com.nollen.blaze.points;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.intake.LiveEvent;
import com.nollen.blaze.intake.LiveEventStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsService.class);

    private final PointsStore store;
    private final IdGenerator idGenerator;

    public PointsService(PointsStore store, IdGenerator idGenerator) {
        this.store = store;
        this.idGenerator = idGenerator;
    }

    public Optional<PointsLedger> creditFromEvent(LiveEvent event) {
        if (event == null || event.status() != LiveEventStatus.ACCEPTED) {
            return Optional.empty();
        }

        // Dedup check
        Optional<PointsLedger> existing = store.findLedgerBySourceEventId(event.id());
        if (existing.isPresent()) {
            log.debug("Duplicate event {}, skipping credit", event.id());
            return Optional.empty();
        }

        // Look up rule by event type
        String eventTypeId = event.type().id();
        Optional<PointsRule> ruleOpt = store.findRuleByEventType(eventTypeId);
        if (ruleOpt.isEmpty() || !ruleOpt.get().enabled()) {
            log.debug("No enabled rule for event type '{}', skipping credit", eventTypeId);
            return Optional.empty();
        }
        PointsRule rule = ruleOpt.get();

        // Extract userId from payload
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            return Optional.empty();
        }

        String userId = extractString(payload, "userId");
        String username = extractString(payload, "userName");
        if (username == null) {
            username = extractString(payload, "username");
        }

        if (userId == null || userId.isBlank()) {
            log.debug("No userId in event payload, skipping credit for event {}", event.id());
            return Optional.empty();
        }

        // Create ledger entry
        String ledgerId = idGenerator.newId();
        PointsLedger ledger = new PointsLedger(
                ledgerId,
                userId,
                username,
                rule.pointsPerEvent(),
                rule.eventType(),
                event.id(),
                Instant.now()
        );

        store.saveLedger(ledger);
        log.info("Credited {} points to user '{}' from event {} (rule: {})",
                rule.pointsPerEvent(), username, event.id(), rule.eventType());

        return Optional.of(ledger);
    }

    public PointsLedger creditManual(String userId, String username, int points, String description) {
        String ledgerId = idGenerator.newId();
        PointsLedger ledger = new PointsLedger(
                ledgerId,
                userId,
                username,
                points,
                "MANUAL",
                null,
                Instant.now()
        );
        store.saveLedger(ledger);
        log.info("Manual credit of {} points to user '{}' ({}). Ledger: {}",
                points, username, description, ledgerId);
        return ledger;
    }

    public Optional<PointsBalance> getBalance(String userId) {
        List<PointsLedger> entries = store.findByUserId(userId);
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        int totalPoints = 0;
        String username = null;
        for (PointsLedger entry : entries) {
            totalPoints += entry.points();
            if (username == null && entry.username() != null) {
                username = entry.username();
            }
        }

        PointsBalance balance = new PointsBalance(
                userId,
                username,
                totalPoints,
                entries.size()
        );
        return Optional.of(balance);
    }

    public List<PointsLeaderboardEntry> getLeaderboard(int limit) {
        List<PointsLedger> allEntries = store.findAllLedgers();

        // Group by userId, sum points and track username
        Map<String, int[]> pointsByUser = new LinkedHashMap<>();
        Map<String, String> usernameByUser = new LinkedHashMap<>();

        for (PointsLedger entry : allEntries) {
            pointsByUser.computeIfAbsent(entry.userId(), k -> new int[]{0})[0] += entry.points();
            if (entry.username() != null) {
                usernameByUser.putIfAbsent(entry.userId(), entry.username());
            }
        }

        // Sort by points descending
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(pointsByUser.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

        // Limit and assign ranks
        List<PointsLeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            Map.Entry<String, int[]> entry = sorted.get(i);
            String uid = entry.getKey();
            String uname = usernameByUser.getOrDefault(uid, uid);
            result.add(new PointsLeaderboardEntry(rank++, uid, uname, entry.getValue()[0]));
        }

        return result;
    }

    public List<PointsLedger> getHistory(String userId) {
        return store.findByUserId(userId);
    }

    public List<PointsRule> getRules() {
        return store.findAllRules();
    }

    public PointsStatsResponse getStats() {
        List<PointsLedger> allEntries = store.findAllLedgers();
        int totalUsers = 0;
        int totalPoints = 0;
        java.util.Set<String> uniqueUsers = new java.util.HashSet<>();

        for (PointsLedger entry : allEntries) {
            totalPoints += entry.points();
            uniqueUsers.add(entry.userId());
        }
        totalUsers = uniqueUsers.size();

        List<PointsRule> rules = store.findAllRules();
        return new PointsStatsResponse(totalUsers, totalPoints, allEntries.size(), rules);
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String s) {
            return s;
        }
        return null;
    }
}
