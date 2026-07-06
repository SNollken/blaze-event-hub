package com.blaze.eventhub.event.draw;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.event.interest.EventInterestService;

@Service
public class DrawService {

    private static final Logger log = LoggerFactory.getLogger(DrawService.class);

    private final EventWinnerStore winnerStore;
    private final EventStore eventStore;
    private final EventInterestService interestService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public DrawService(EventWinnerStore winnerStore, EventStore eventStore,
            EventInterestService interestService,
            IdGenerator idGenerator, Clock clock) {
        this.winnerStore = winnerStore;
        this.eventStore = eventStore;
        this.interestService = interestService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public EventWinner executeDraw(String eventId, String creatorMemberId) {
        Event event = eventStore.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));

        if (event.status() != EventStatus.CLOSED) {
            throw new IllegalStateException("Evento precisa estar CLOSED para sortear. Status: " + event.status());
        }

        if (winnerStore.findByEventId(eventId).isPresent()) {
            throw new IllegalStateException("Evento ja possui um vencedor.");
        }

        var participants = interestService.getParticipants(eventId);
        if (participants.isEmpty()) {
            throw new IllegalStateException("Nenhum participante elegivel para sorteio.");
        }

        var random = new java.util.Random();
        int index = random.nextInt(participants.size());
        var winner = participants.get(index);

        String drawSeed = String.valueOf(System.nanoTime());
        Instant now = Instant.now(clock);

        int entriesAtDrawTime = winner.lastCalculatedEntries();

        EventWinner eventWinner = new EventWinner(
                idGenerator.newId(),
                eventId,
                winner.memberId(),
                entriesAtDrawTime,
                drawSeed,
                "simple_random",
                now,
                creatorMemberId,
                "Sorteado entre " + participants.size() + " participantes");

        winnerStore.save(eventWinner);

        // Atualiza status do evento para COMPLETED
        eventStore.updateStatus(eventId, EventStatus.COMPLETED);

        log.info("Sorteio executado: eventId={}, winner={}, participantes={}",
                eventId, winner.memberId(), participants.size());

        return eventWinner;
    }

    public Optional<EventWinner> getWinner(String eventId) {
        return winnerStore.findByEventId(eventId);
    }

    public List<EventWinner> getAllWinners(String eventId) {
        return winnerStore.findAllByEventId(eventId);
    }
}
