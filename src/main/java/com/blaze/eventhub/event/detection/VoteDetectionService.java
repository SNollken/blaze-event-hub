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
import com.blaze.eventhub.event.interest.EventInterest;
import com.blaze.eventhub.event.interest.EventInterestService;
import com.blaze.eventhub.event.interest.InterestStatus;

@Service
public class VoteDetectionService {

    private static final Logger log = LoggerFactory.getLogger(VoteDetectionService.class);

    private final DetectedActionStore detectedActionStore;
    private final EventStore eventStore;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Autowired(required = false)
    private EventInterestService interestService;

    public VoteDetectionService(DetectedActionStore detectedActionStore, EventStore eventStore,
            IdGenerator idGenerator, Clock clock) {
        this.detectedActionStore = detectedActionStore;
        this.eventStore = eventStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public DetectedAction processVoteEvent(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        String targetChannelId = getString(payload, "channelId");
        if (targetChannelId == null || targetChannelId.isBlank()) {
            return null;
        }

        Map<String, Object> user = getMap(payload, "user");
        if (user == null) {
            return null;
        }

        String actorBlazeUserId = getString(user, "id");
        String actorUsername = getString(user, "username");
        if (actorBlazeUserId == null || actorBlazeUserId.isBlank()) {
            return null;
        }

        String voteType = getString(payload, "type");
        if (voteType == null || voteType.isBlank()) {
            voteType = "vote";
        }

        Event event = findActiveEventForChannel(targetChannelId);
        if (event == null) {
            return null;
        }

        String idempotencyHash = computeIdempotencyHash(event.id(), actorBlazeUserId, voteType);

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
                "vote",
                targetChannelId,
                actorBlazeUserId,
                actorUsername,
                null,
                1,
                payload.toString(),
                now,
                false,
                now);

        detectedActionStore.save(action);

        log.info("Vote detected: eventId={}, actor={}, channel={}", event.id(), actorUsername, targetChannelId);

        return action;
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
