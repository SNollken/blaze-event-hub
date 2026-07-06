package com.blaze.eventhub.event.interest;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.member.Member;
import com.blaze.eventhub.member.MemberService;

@Service
public class EventInterestService {

    private static final Logger log = LoggerFactory.getLogger(EventInterestService.class);

    private final EventInterestStore interestStore;
    private final EventStore eventStore;
    private final MemberService memberService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public EventInterestService(EventInterestStore interestStore, EventStore eventStore,
            MemberService memberService, IdGenerator idGenerator, Clock clock) {
        this.interestStore = interestStore;
        this.eventStore = eventStore;
        this.memberService = memberService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public EventInterest expressInterest(String eventId, String memberId) {
        Event event = eventStore.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        if (event.status() != EventStatus.OPEN) {
            throw new IllegalArgumentException("Event is not open. Current status: " + event.status().name().toLowerCase());
        }

        if (event.creatorMemberId().equals(memberId)) {
            throw new IllegalArgumentException("Event creator cannot express interest in their own event");
        }

        if (interestStore.existsByEventIdAndMemberId(eventId, memberId)) {
            throw new IllegalArgumentException("Already expressed interest in this event");
        }

        Instant now = Instant.now(clock);
        EventInterest interest = new EventInterest(
                idGenerator.newId(),
                eventId,
                memberId,
                InterestStatus.INTERESTED,
                now,
                0,
                null,
                now,
                now);

        interestStore.save(interest);

        log.info("Interest expressed: eventId={}, memberId={}", eventId, memberId);

        return interest;
    }

    public EventInterest withdrawInterest(String eventId, String memberId) {
        EventInterest existing = interestStore.findByEventIdAndMemberId(eventId, memberId)
                .orElseThrow(() -> new NotFoundException("No interest found for this event and member"));

        Instant now = Instant.now(clock);
        EventInterest updated = new EventInterest(
                existing.id(),
                existing.eventId(),
                existing.memberId(),
                InterestStatus.WITHDRAWN,
                existing.interestedAt(),
                existing.lastCalculatedEntries(),
                existing.notes(),
                existing.createdAt(),
                now);

        interestStore.save(updated);

        log.info("Interest withdrawn: eventId={}, memberId={}", eventId, memberId);

        return updated;
    }

    public List<EventInterestResponse> getParticipants(String eventId) {
        List<EventInterest> interests = interestStore.findByEventId(eventId);
        List<EventInterestResponse> responses = new ArrayList<>();

        for (EventInterest interest : interests) {
            try {
                Member member = memberService.findById(interest.memberId());
                responses.add(new EventInterestResponse(
                        member.id(),
                        member.blazeUsername(),
                        member.displayName(),
                        interest.status(),
                        interest.interestedAt(),
                        interest.lastCalculatedEntries()));
            } catch (NotFoundException e) {
                responses.add(new EventInterestResponse(
                        interest.memberId(),
                        "unknown",
                        "Unknown",
                        interest.status(),
                        interest.interestedAt(),
                        interest.lastCalculatedEntries()));
            }
        }

        return responses;
    }

    public Optional<EventInterest> findByEventIdAndMemberId(String eventId, String memberId) {
        return interestStore.findByEventIdAndMemberId(eventId, memberId);
    }

    public List<EventInterest> findByEventId(String eventId) {
        return interestStore.findByEventId(eventId);
    }

    public void updateEntries(String eventId, String memberId, int entries) {
        interestStore.findByEventIdAndMemberId(eventId, memberId).ifPresent(existing -> {
            Instant now = Instant.now(clock);
            EventInterest updated = new EventInterest(
                    existing.id(), existing.eventId(), existing.memberId(),
                    entries > 0 ? InterestStatus.ELIGIBLE : InterestStatus.INTERESTED,
                    existing.interestedAt(), entries, existing.notes(),
                    existing.createdAt(), now);
            interestStore.save(updated);
        });
    }
}
