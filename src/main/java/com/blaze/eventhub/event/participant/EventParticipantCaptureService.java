package com.blaze.eventhub.event.participant;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.event.ActionType;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventActionRuleService;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;

@Service
public class EventParticipantCaptureService {

    private final EventStore eventStore;
    private final EventParticipantStore participantStore;
    private final EventActionRuleService actionRuleService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public EventParticipantCaptureService(
            EventStore eventStore,
            EventParticipantStore participantStore,
            EventActionRuleService actionRuleService,
            IdGenerator idGenerator,
            Clock clock) {
        this.eventStore = eventStore;
        this.participantStore = participantStore;
        this.actionRuleService = actionRuleService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public CaptureResult capture(ChatEntryCandidate candidate) {
        if (!hasRequiredIdentity(candidate)) {
            return CaptureResult.ignored("invalid_chat_message");
        }

        String actionTypeStr = candidate.actionType() != null ? candidate.actionType() : "chat";
        ActionType actionType = ActionType.fromString(actionTypeStr);

        Event matchingEvent = eventStore.findCapturingByChannelId(candidate.channelId().trim()).stream()
                .filter(event -> isActionEnabledForEvent(event, actionType)
                        && ChatEntryMatcher.matches(event.entryCommand(), candidate.message()))
                .findFirst()
                .orElse(null);
        if (matchingEvent == null) {
            return CaptureResult.ignored("no_matching_open_event");
        }

        Event lockedEvent = eventStore.findByIdForUpdate(matchingEvent.id()).orElse(null);
        if (lockedEvent == null
                || (lockedEvent.status() != EventStatus.OPEN
                        && lockedEvent.status() != EventStatus.FINALIZING)
                || !lockedEvent.creatorChannelId().equals(candidate.channelId().trim())
                || !ChatEntryMatcher.matches(lockedEvent.entryCommand(), candidate.message())) {
            return CaptureResult.ignored("event_not_accepting_entries");
        }

        if (candidate.sentAt() != null
                && lockedEvent.openedAt() != null
                && candidate.sentAt().isBefore(lockedEvent.openedAt())) {
            return CaptureResult.ignored("message_predates_event_opening");
        }

        if (lockedEvent.status() == EventStatus.FINALIZING
                && (lockedEvent.finalizationCutoffAt() == null
                        || candidate.sentAt().isAfter(lockedEvent.finalizationCutoffAt()))) {
            return CaptureResult.ignored("message_after_finalization_cutoff");
        }

        int weight = actionRuleService.weight(lockedEvent.id(), actionType);

        Instant now = Instant.now(clock);
        EventParticipant participant = new EventParticipant(
                idGenerator.newId(),
                lockedEvent.id(),
                candidate.blazeUserId().trim(),
                trimmedOrNull(candidate.blazeUsername()),
                displayName(candidate),
                candidate.messageId().trim(),
                actionType.value(),
                weight,
                candidate.sentAt() != null ? candidate.sentAt() : now,
                now);

        return participantStore.saveIfAbsent(participant)
                ? CaptureResult.accepted(lockedEvent.id())
                : CaptureResult.duplicate(lockedEvent.id());
    }

    private boolean isActionEnabledForEvent(Event event, ActionType actionType) {
        return actionRuleService.isEnabled(event.id(), actionType);
    }

    private static boolean hasRequiredIdentity(ChatEntryCandidate candidate) {
        return candidate != null
                && hasText(candidate.channelId())
                && hasText(candidate.messageId())
                && hasText(candidate.message())
                && hasText(candidate.blazeUserId())
                && candidate.sentAt() != null;
    }

    private static String displayName(ChatEntryCandidate candidate) {
        String displayName = trimmedOrNull(candidate.displayName());
        if (displayName != null) {
            return displayName;
        }
        String username = trimmedOrNull(candidate.blazeUsername());
        return username != null ? username : candidate.blazeUserId().trim();
    }

    private static String trimmedOrNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
