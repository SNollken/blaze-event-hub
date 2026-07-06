package com.blaze.eventhub.event;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.event.audit.AuditService;
import com.blaze.eventhub.member.MemberService;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventStore eventStore;
    private final EventRuleStore eventRuleStore;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Autowired(required = false)
    private MemberService memberService;

    @Autowired(required = false)
    private AuditService auditService;

    public EventService(EventStore eventStore, EventRuleStore eventRuleStore, IdGenerator idGenerator, Clock clock) {
        this.eventStore = eventStore;
        this.eventRuleStore = eventRuleStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public EventResponse createEvent(CreateEventRequest request, String creatorMemberId, String creatorBlazeUserId, String creatorChannelId) {
        validateCreateRequest(request);

        Instant now = Instant.now(clock);
        String eventId = idGenerator.newId();

        RulesMode rulesMode = RulesMode.fromDb(request.rulesMode());

        Event event = new Event(
                eventId,
                creatorMemberId,
                creatorBlazeUserId,
                creatorChannelId,
                request.title().trim(),
                request.description(),
                request.prizeType(),
                request.prizeDescription(),
                EventStatus.DRAFT,
                rulesMode,
                request.maxEntriesPerParticipant(),
                request.requiresInterestBeforeAction(),
                parseInstant(request.startsAt()),
                parseInstant(request.endsAt()),
                now,
                now,
                null,
                null);

        eventStore.save(event);

        List<EventRule> savedRules = new ArrayList<>();
        for (CreateEventRuleRequest ruleRequest : request.rules()) {
            EventRule rule = createEventRule(eventId, ruleRequest, now);
            savedRules.add(rule);
        }

        log.info("Event created: id={}, title={}, creator={}, rulesCount={}", eventId, request.title(), creatorMemberId, savedRules.size());

        return EventResponse.from(event, savedRules);
    }

    public EventResponse getEvent(String eventId) {
        Event event = eventStore.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        List<EventRule> rules = eventRuleStore.findByEventId(eventId);
        return EventResponse.from(event, rules);
    }

    public List<EventResponse> listEvents(String statusFilter) {
        List<Event> events;
        if (statusFilter != null && !statusFilter.isBlank()) {
            EventStatus status = EventStatus.fromDb(statusFilter);
            events = eventStore.findByStatus(status);
        } else {
            events = eventStore.findAll();
        }

        return events.stream()
                .map(e -> {
                    List<EventRule> rules = eventRuleStore.findByEventId(e.id());
                    return EventResponse.from(e, rules);
                })
                .toList();
    }

    public List<EventResponse> findByCreatorMemberId(String memberId) {
        return eventStore.findByCreatorMemberId(memberId).stream()
                .map(e -> {
                    List<EventRule> rules = eventRuleStore.findByEventId(e.id());
                    return EventResponse.from(e, rules);
                })
                .toList();
    }

    public EventResponse updateEvent(String eventId, UpdateEventRequest request, String memberId) {
        Event event = requireEventOwnership(eventId, memberId);

        Instant now = Instant.now(clock);

        Event updated = new Event(
                event.id(),
                event.creatorMemberId(),
                event.creatorBlazeUserId(),
                event.creatorChannelId(),
                coalesce(request.title(), event.title()),
                has(request.description()) ? request.description() : event.description(),
                has(request.prizeType()) ? request.prizeType() : event.prizeType(),
                has(request.prizeDescription()) ? request.prizeDescription() : event.prizeDescription(),
                event.status(),
                request.rulesMode() != null ? RulesMode.fromDb(request.rulesMode()) : event.rulesMode(),
                request.maxEntriesPerParticipant() >= 0 ? request.maxEntriesPerParticipant() : event.maxEntriesPerParticipant(),
                request.requiresInterestBeforeAction() != null ? request.requiresInterestBeforeAction() : event.requiresInterestBeforeAction(),
                has(request.startsAt()) ? parseInstant(request.startsAt()) : event.startsAt(),
                has(request.endsAt()) ? parseInstant(request.endsAt()) : event.endsAt(),
                event.createdAt(),
                now,
                event.closedAt(),
                event.completedAt());

        eventStore.save(updated);

        List<EventRule> rules = eventRuleStore.findByEventId(eventId);
        return EventResponse.from(updated, rules);
    }

    public EventResponse openEvent(String eventId, String memberId) {
        Event event = requireEventOwnership(eventId, memberId);

        if (event.status() != EventStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft events can be opened. Current status: " + event.status().name().toLowerCase());
        }

        eventStore.updateStatus(eventId, EventStatus.OPEN);

        if (auditService != null) {
            auditService.log("event_opened", "event", eventId,
                    event.status().name().toLowerCase(), "open", memberId);
        }

        return getEvent(eventId);
    }

    public EventResponse closeEvent(String eventId, String memberId) {
        Event event = requireEventOwnership(eventId, memberId);

        if (event.status() != EventStatus.OPEN) {
            throw new IllegalArgumentException("Only open events can be closed. Current status: " + event.status().name().toLowerCase());
        }

        eventStore.updateStatus(eventId, EventStatus.CLOSED);

        if (auditService != null) {
            auditService.log("event_closed", "event", eventId,
                    "open", "closed", memberId);
        }

        return getEvent(eventId);
    }

    public EventResponse cancelEvent(String eventId, String memberId) {
        Event event = requireEventOwnership(eventId, memberId);

        if (event.status() == EventStatus.COMPLETED || event.status() == EventStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot cancel an event that is already " + event.status().name().toLowerCase());
        }

        String previousStatus = event.status().name().toLowerCase();
        eventStore.updateStatus(eventId, EventStatus.CANCELLED);

        if (auditService != null) {
            auditService.log("event_cancelled", "event", eventId,
                    previousStatus, "cancelled", memberId);
        }

        return getEvent(eventId);
    }

    public EventRuleResponse addRule(String eventId, CreateEventRuleRequest request, String memberId) {
        requireEventOwnership(eventId, memberId);

        Event event = eventStore.findById(eventId).get();

        if (event.status() != EventStatus.DRAFT) {
            auditViolation(eventId, memberId, "rule_added_after_opened", "Attempt to add rule to non-draft event");
            throw new IllegalArgumentException("Cannot add rules to an event that is not in draft status. Current status: " + event.status().name().toLowerCase());
        }

        Instant now = Instant.now(clock);
        EventRule rule = createEventRule(eventId, request, now);

        return EventRuleResponse.from(rule);
    }

    public EventRuleResponse updateRule(String eventId, String ruleId, UpdateEventRuleRequest request, String memberId) {
        requireEventOwnership(eventId, memberId);

        Event event = eventStore.findById(eventId).get();

        if (event.status() != EventStatus.DRAFT) {
            auditViolation(eventId, memberId, "rule_updated_after_opened", "Attempt to update rule " + ruleId + " on non-draft event");
            throw new IllegalArgumentException("Cannot update rules on an event that is not in draft status. Current status: " + event.status().name().toLowerCase());
        }

        List<EventRule> existingRules = eventRuleStore.findByEventId(eventId);
        EventRule existing = existingRules.stream()
                .filter(r -> r.id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));

        Instant now = Instant.now(clock);

        EventRule updated = new EventRule(
                existing.id(),
                existing.eventId(),
                request.actionType() != null ? ActionType.fromDb(request.actionType()) : existing.actionType(),
                request.thresholdAmount() > 0 ? request.thresholdAmount() : existing.thresholdAmount(),
                request.entries() > 0 ? request.entries() : existing.entries(),
                request.isActive() != null ? request.isActive() : existing.isActive(),
                existing.createdAt(),
                now);

        eventRuleStore.update(updated);

        return EventRuleResponse.from(updated);
    }

    public void removeRule(String eventId, String ruleId, String memberId) {
        requireEventOwnership(eventId, memberId);

        Event event = eventStore.findById(eventId).get();

        if (event.status() != EventStatus.DRAFT) {
            auditViolation(eventId, memberId, "rule_removed_after_opened", "Attempt to remove rule " + ruleId + " on non-draft event");
            throw new IllegalArgumentException("Cannot remove rules from an event that is not in draft status. Current status: " + event.status().name().toLowerCase());
        }

        int deleted = eventRuleStore.delete(ruleId);
        if (deleted == 0) {
            throw new NotFoundException("Rule not found: " + ruleId);
        }
    }

    // --- Private helpers ---

    private void validateCreateRequest(CreateEventRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        if (request.rules() == null || request.rules().isEmpty()) {
            throw new IllegalArgumentException("At least one rule is required");
        }

        for (CreateEventRuleRequest rule : request.rules()) {
            if (rule.thresholdAmount() <= 0) {
                throw new IllegalArgumentException("Rule thresholdAmount must be positive");
            }
            if (rule.entries() <= 0) {
                throw new IllegalArgumentException("Rule entries must be positive");
            }
        }
    }

    private EventRule createEventRule(String eventId, CreateEventRuleRequest request, Instant now) {
        EventRule rule = new EventRule(
                idGenerator.newId(),
                eventId,
                ActionType.fromDb(request.actionType()),
                request.thresholdAmount(),
                request.entries(),
                true,
                now,
                now);
        eventRuleStore.save(rule);
        return rule;
    }

    private Event requireEventOwnership(String eventId, String memberId) {
        Event event = eventStore.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        if (!event.creatorMemberId().equals(memberId)) {
            throw new IllegalArgumentException("Only the event creator can perform this action");
        }

        return event;
    }

    private void auditViolation(String eventId, String memberId, String action, String description) {
        log.warn("AUDIT: eventId={}, memberId={}, action={}, description={}", eventId, memberId, action, description);
        if (auditService != null) {
            auditService.log(action, "event", eventId, null, description, memberId);
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format: " + value + ". Expected ISO-8601 format (e.g., 2026-07-06T10:00:00Z)");
        }
    }

    private static String coalesce(String newValue, String existing) {
        return newValue != null ? newValue : existing;
    }

    private static boolean has(String value) {
        return value != null;
    }
}
