package com.blaze.eventhub.event.entry;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventRule;
import com.blaze.eventhub.event.EventRuleStore;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.detection.DetectedAction;
import com.blaze.eventhub.event.detection.DetectedActionStore;

@Service
public class EntryCalculator {

    private static final Logger log = LoggerFactory.getLogger(EntryCalculator.class);

    private final EventEntryStore entryStore;
    private final DetectedActionStore actionStore;
    private final EventRuleStore ruleStore;
    private final EventStore eventStore;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public EntryCalculator(EventEntryStore entryStore, DetectedActionStore actionStore,
            EventRuleStore ruleStore, EventStore eventStore,
            IdGenerator idGenerator, Clock clock) {
        this.entryStore = entryStore;
        this.actionStore = actionStore;
        this.ruleStore = ruleStore;
        this.eventStore = eventStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public List<EventEntry> calculateEntries(String eventId, String memberId) {
        Event event = eventStore.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento nao encontrado: " + eventId));

        List<DetectedAction> actions = actionStore.findByEventIdAndMemberId(eventId, memberId);
        List<EventRule> rules = ruleStore.findByEventId(eventId).stream()
                .filter(EventRule::isActive)
                .toList();

        if (rules.isEmpty()) {
            log.debug("No active rules for event {}", eventId);
            return List.of();
        }

        List<EventEntry> results = new ArrayList<>();

        var actionTotals = new java.util.HashMap<String, Integer>();
        var actionCounts = new java.util.HashMap<String, Integer>();
        for (DetectedAction action : actions) {
            actionTotals.merge(action.actionType(), action.amount(), Integer::sum);
            actionCounts.merge(action.actionType(), 1, Integer::sum);
        }

        for (var entry : actionTotals.entrySet()) {
            String actionType = entry.getKey();
            int totalAmount = entry.getValue();
            int actionCount = actionCounts.getOrDefault(actionType, 0);

            var matchingRules = rules.stream()
                    .filter(r -> r.actionType().name().equalsIgnoreCase(actionType))
                    .sorted(Comparator.comparingInt(EventRule::thresholdAmount).reversed())
                    .toList();

            if (matchingRules.isEmpty()) continue;

            EventRule bestRule = null;
            for (EventRule rule : matchingRules) {
                if (totalAmount >= rule.thresholdAmount()) {
                    bestRule = rule;
                    break;
                }
            }

            int entriesGranted = bestRule != null ? bestRule.entries() : 0;

            int maxEntries = event.maxEntriesPerParticipant();
            int currentTotal = entryStore.countByEventIdAndMemberId(eventId, memberId);
            if (maxEntries > 0 && currentTotal + entriesGranted > maxEntries) {
                entriesGranted = Math.max(0, maxEntries - currentTotal);
            }

            String reason = bestRule != null
                    ? String.format("Tier mode: %s total=%d (%d actions), matched rule: threshold=%d -> %d entries",
                            actionType, totalAmount, actionCount,
                            bestRule.thresholdAmount(), entriesGranted)
                    : String.format("Tier mode: %s total=%d (%d actions), no matching rule",
                            actionType, totalAmount, actionCount);

            if (entriesGranted > 0) {
                Instant now = Instant.now(clock);
                EventEntry eventEntry = new EventEntry(
                        idGenerator.newId(), eventId, memberId, null,
                        actionType, totalAmount, entriesGranted, reason, now);
                results.add(entryStore.save(eventEntry));
            }

            log.info("Entries: eventId={}, memberId={}, {} total={}, entries={}",
                    eventId, memberId, actionType, totalAmount, entriesGranted);
        }

        return results;
    }

    public int getTotalEntries(String eventId, String memberId) {
        return entryStore.countByEventIdAndMemberId(eventId, memberId);
    }

    public List<EventEntry> getEntries(String eventId, String memberId) {
        return entryStore.findByEventIdAndMemberId(eventId, memberId);
    }
}
