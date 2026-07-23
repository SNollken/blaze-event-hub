package com.blaze.eventhub.events;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.event.ActionType;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventActionRuleService;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.participant.CaptureResult;
import com.blaze.eventhub.event.participant.ChatEntryCandidate;
import com.blaze.eventhub.event.participant.EventParticipantCaptureService;
import com.blaze.eventhub.oauth.PersistentOAuthCredentialService;
import com.blaze.eventhub.oauth.TokenSnapshot;

@Service
public class BlazeEventsPipeline {

    private static final Logger log = LoggerFactory.getLogger(BlazeEventsPipeline.class);

    private final BlazeProperties properties;
    private final PersistentOAuthCredentialService credentialService;
    private final EventStore eventStore;
    private final EventActionRuleService actionRuleService;
    private final EventParticipantCaptureService captureService;

    public BlazeEventsPipeline(
            BlazeProperties properties,
            PersistentOAuthCredentialService credentialService,
            EventStore eventStore,
            EventActionRuleService actionRuleService,
            EventParticipantCaptureService captureService) {
        this.properties = properties;
        this.credentialService = credentialService;
        this.eventStore = eventStore;
        this.actionRuleService = actionRuleService;
        this.captureService = captureService;
    }

    /**
     * Process a real-time Blaze event (vote, sub, gifted_sub, follow, tip, chat).
     * This is called from the Socket.IO client when a real-time event is received.
     */
    @Transactional
    public void processActionEvent(String actionType, Map<String, Object> payload) {
        try {
            ActionType type = ActionType.fromString(actionType);

            // Extract user and channel info from Blaze payload
            Map<String, Object> user = extractUser(payload);
            if (user == null) {
                log.warn("Could not extract user from {} payload: {}", actionType, actionType);
                return;
            }

            String channelId = extractChannelId(payload);
            if (channelId == null) {
                log.warn("Could not extract channelId from {} payload", actionType);
                return;
            }

            String blazeUserId = (String) user.get("id");
            String username = (String) user.get("username");
            String displayName = (String) user.get("displayName");
            String avatarUrl = (String) user.get("avatarUrl");

            if (blazeUserId == null) {
                log.warn("No blazeUserId in {} payload", actionType);
                return;
            }

            // Find open events for this channel
            List<Event> openEvents = eventStore.findCapturingByChannelId(channelId).stream()
                    .filter(e -> e.status() == EventStatus.OPEN || e.status() == EventStatus.FINALIZING)
                    .filter(e -> actionRuleService.isEnabled(e.id(), ActionType.fromString(actionType.toLowerCase())))
                    .toList();

            if (openEvents.isEmpty()) {
                log.debug("No open events for channel {} with action type {}", channelId, actionType);
                return;
            }

            // Create candidate for each matching event
            for (Event event : openEvents) {
                ChatEntryCandidate candidate = ChatEntryCandidate.builder()
                        .channelId(channelId)
                        .messageId(actionType + "_" + System.currentTimeMillis() + "_" + user.get("id"))
                        .message("") // Socket events don't have a chat message
                        .blazeUserId(blazeUserId)
                        .blazeUsername(username)
                        .displayName(displayName)
                        .actionType(actionType.toLowerCase())
                        .sentAt(java.time.Instant.now())
                        .build();

                CaptureResult result = captureService.capture(candidate);
                if (result.status() == com.blaze.eventhub.event.participant.CaptureStatus.ACCEPTED) {
                    log.info("Captured {} entry for user {} in event {}",
                            actionType, user.get("id"), result.eventId());
                }
            }

        } catch (Exception e) {
            log.error("Failed to process {} event", actionType, e);
        }
    }

    /**
     * Get all open channel IDs for a member.
     * Used for subscribing to Blaze Socket.IO events.
     */
    public List<String> getOpenChannelIdsForMember(String memberId) {
        return eventStore.findByCreatorMemberId(memberId).stream()
                .filter(e -> e.status() == EventStatus.OPEN || e.status() == EventStatus.FINALIZING)
                .map(Event::creatorChannelId)
                .distinct()
                .toList();
    }

    // Helper methods to extract data from Blaze payloads

    private String extractChannelId(Map<String, Object> payload) {
        Object channelId = payload.get("channelId");
        return channelId != null ? channelId.toString() : null;
    }

    private Map<String, Object> extractUser(Map<String, Object> payload) {
        // channel.vote -> payload.user
        // channel.subscribe -> payload.subscriber
        // channel.subscription.gift -> payload.gifter
        // channel.follow -> payload.follower
        // channel.tip -> payload.user or payload.tipper

        String[] userKeys = {"user", "subscriber", "gifter", "follower", "tipper", "user"};
        for (String key : userKeys) {
            Object user = payload.get(key);
            if (user instanceof Map) {
                return (Map<String, Object>) user;
            }
        }
        return null;
    }
}