package com.blaze.eventhub.event.detection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.entry.EntryCalculator;
import com.blaze.eventhub.event.interest.EventInterest;
import com.blaze.eventhub.event.interest.EventInterestService;
import com.blaze.eventhub.event.interest.InterestStatus;

@Service
public class ActionDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ActionDetectionService.class);

    private final DetectedActionStore detectedActionStore;
    private final EventStore eventStore;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Autowired(required = false)
    private EventInterestService interestService;

    @Autowired(required = false)
    private EntryCalculator entryCalculator;

    public ActionDetectionService(DetectedActionStore detectedActionStore, EventStore eventStore,
            IdGenerator idGenerator, Clock clock) {
        this.detectedActionStore = detectedActionStore;
        this.eventStore = eventStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** Process any detected action: vote, sub, gifted_sub */
    public DetectedAction processActionEvent(String actionType, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        if (actionType == null || actionType.isBlank()) {
            actionType = "vote";
        }

        String targetChannelId = getString(payload, "channelId");
        if (targetChannelId == null || targetChannelId.isBlank()) {
            return null;
        }

        // Resolve user: vote has "user", sub/gifted_sub have "subscriber" or "sender"
        Map<String, Object> user = getMap(payload, "user");
        if (user == null) user = getMap(payload, "subscriber");
        if (user == null) user = getMap(payload, "gifter");
        if (user == null) return null;

        String actorBlazeUserId = getString(user, "id");
        String actorUsername = getString(user, "username");
        if (actorBlazeUserId == null || actorBlazeUserId.isBlank()) {
            return null;
        }

        // Amount: votes have no amount field (default 1), subs may have "tier", gifted_subs "amount"
        int amount = 1;
        Object amountObj = payload.get("amount");
        if (amountObj instanceof Number n) {
            amount = n.intValue();
            if (amount <= 0) amount = 1;
        }

        Event event = findActiveEventForChannel(targetChannelId);
        if (event == null) {
            return null;
        }

        String idempotencyHash = computeIdempotencyHash(event.id(), actorBlazeUserId, actionType);

        if (detectedActionStore.findByIdempotencyHash(idempotencyHash).isPresent()) {
            return null;
        }

        String memberId = resolveMemberId(actorBlazeUserId, event.id());

        Instant now = Instant.now(clock);
        DetectedAction action = new DetectedAction(
                idGenerator.newId(),
                idempotencyHash,
                event.id(),
                memberId != null ? memberId : actorBlazeUserId,
                actionType,
                targetChannelId,
                actorBlazeUserId,
                actorUsername,
                null,
                amount,
                payload.toString(),
                now,
                false,
                now);

        detectedActionStore.save(action);

        log.info("Action detected: type={}, eventId={}, actor={}, channel={}, amount={}",
                actionType, event.id(), actorUsername, targetChannelId, amount);

        if (entryCalculator != null) {
            try {
                entryCalculator.calculateEntries(event.id(), action.memberId());
            } catch (Exception e) {
                log.warn("Entry calculation failed for {} action: {}", actionType, e.getMessage());
            }
        }

        return action;
    }

    /** Backwards-compatible alias for existing callers */
    public DetectedAction processVoteEvent(Map<String, Object> payload) {
        return processActionEvent("vote", payload);
    }

    private Event findActiveEventForChannel(String channelId) {
        var events = eventStore.findByStatus(EventStatus.OPEN);
        for (Event event : events) {
            if (channelId.equals(event.creatorChannelId())) {
                return event;
            }
        }
        return null;
    }

    private String resolveMemberId(String blazeUserId, String eventId) {
        if (interestService == null) {
            return null;
        }
        return interestService.findByEventIdAndMemberId(eventId, blazeUserId)
                .map(EventInterest::memberId)
                .orElse(null);
    }

    private static String computeIdempotencyHash(String eventId, String userId, String actionType) {
        String raw = eventId + ":" + userId + ":" + actionType;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return raw;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }
}
