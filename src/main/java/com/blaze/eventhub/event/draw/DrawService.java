package com.blaze.eventhub.event.draw;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.common.ConflictException;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.participant.EventParticipant;
import com.blaze.eventhub.event.participant.EventParticipantStore;
import com.blaze.eventhub.event.participant.ParticipantPoolHasher;

@Service
public class DrawService {

    private static final Logger log = LoggerFactory.getLogger(DrawService.class);
    private static final String DRAW_METHOD = "weighted_blaze_participants_v1";

    private final EventWinnerStore winnerStore;
    private final EventStore eventStore;
    private final EventParticipantStore participantStore;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final RandomGenerator seedGenerator;

    @Autowired
    public DrawService(
            EventWinnerStore winnerStore,
            EventStore eventStore,
            EventParticipantStore participantStore,
            IdGenerator idGenerator,
            Clock clock) {
        this(winnerStore, eventStore, participantStore, idGenerator, clock, new SecureRandom());
    }

    DrawService(
            EventWinnerStore winnerStore,
            EventStore eventStore,
            EventParticipantStore participantStore,
            IdGenerator idGenerator,
            Clock clock,
            RandomGenerator seedGenerator) {
        this.winnerStore = winnerStore;
        this.eventStore = eventStore;
        this.participantStore = participantStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.seedGenerator = seedGenerator;
    }

    @Transactional
    public EventWinner executeDraw(String eventId, String creatorMemberId) {
        Event event = eventStore.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));

        if (!event.creatorMemberId().equals(creatorMemberId)) {
            throw new ForbiddenException("Somente o criador pode realizar esta acao.");
        }

        Optional<EventWinner> existingResult = winnerStore.findByEventId(eventId);
        if (event.status() == EventStatus.COMPLETED) {
            return existingResult.orElseThrow(
                    () -> new IllegalStateException("Evento concluido sem resultado persistido."));
        }

        if (event.status() != EventStatus.CLOSED) {
            throw new ConflictException("O evento precisa estar finalizado antes do sorteio. Status: " + event.status());
        }

        if (existingResult.isPresent()) {
            throw new ConflictException("O evento finalizado ja possui um resultado inconsistente.");
        }

        List<EventParticipant> participants = participantStore.findByEventId(eventId).stream()
                .sorted(Comparator.comparing(EventParticipant::blazeUserId))
                .toList();
        if (participants.isEmpty()) {
            throw new ConflictException("Nenhum participante elegivel para sorteio.");
        }

        String currentPoolHash = ParticipantPoolHasher.sha256(participants);
        if (event.finalizedParticipantCount() != participants.size()
                || !currentPoolHash.equals(event.finalizedPoolHash())) {
            throw new ConflictException("O pool de participantes diverge do snapshot finalizado.");
        }

        long seed = seedGenerator.nextLong();
        SplittableRandom rng = new SplittableRandom(seed);
        int totalWeight = participants.stream().mapToInt(EventParticipant::entryWeight).sum();
        int threshold = rng.nextInt(totalWeight);
        int cumulative = 0;
        EventParticipant winner = null;
        for (EventParticipant p : participants) {
            cumulative += p.entryWeight();
            if (cumulative > threshold) {
                winner = p;
                break;
            }
        }
        Instant now = Instant.now(clock);

        EventWinner result = new EventWinner(
                idGenerator.newId(),
                eventId,
                winner.blazeUserId(),
                winner.blazeUsername(),
                winner.displayName(),
                Long.toString(seed),
                DRAW_METHOD,
                currentPoolHash,
                participants.size(),
                now,
                creatorMemberId);

        winnerStore.save(result);
        if (eventStore.completeEvent(eventId, now) != 1) {
            throw new ConflictException("O sorteio nao concluiu exatamente um evento.");
        }

        log.info("Sorteio executado: eventId={}, participantes={}", eventId, participants.size());
        return result;
    }

    public Optional<EventWinner> getWinner(String eventId) {
        return winnerStore.findByEventId(eventId);
    }

    public List<EventWinner> getAllWinners(String eventId) {
        return winnerStore.findAllByEventId(eventId);
    }
}
