package com.nollen.blaze.giveaway;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.nollen.blaze.common.ConflictException;
import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.springframework.stereotype.Service;

@Service
public class GiveawayService {

	private static final int DEFAULT_MAX_ENTRIES = 1000;
	private static final int DEFAULT_WINNER_COUNT = 1;

	private final InMemoryGiveawayStore giveawayStore;
	private final InMemoryGiveawayEntryStore entryStore;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public GiveawayService(InMemoryGiveawayStore giveawayStore, InMemoryGiveawayEntryStore entryStore,
			IdGenerator idGenerator, Clock clock) {
		this.giveawayStore = giveawayStore;
		this.entryStore = entryStore;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public List<Giveaway> listGiveaways() {
		return giveawayStore.findAll();
	}

	public Giveaway getGiveaway(String id) {
		return giveawayStore.findById(id)
				.orElseThrow(() -> new NotFoundException("Giveaway not found"));
	}

	public Giveaway createGiveaway(CreateGiveawayRequest request) {
		Instant now = Instant.now(clock);
		int maxEntries = request.maxEntries() == null ? DEFAULT_MAX_ENTRIES : request.maxEntries();
		Giveaway giveaway = new Giveaway(
				idGenerator.newId(),
				request.title().trim(),
				request.description() == null ? "" : request.description().trim(),
				GiveawayStatus.DRAFT,
				0,
				maxEntries,
				now,
				null,
				null,
				null,
				List.of());
		return giveawayStore.save(giveaway);
	}

	public Giveaway updateGiveaway(String id, UpdateGiveawayRequest request) {
		Giveaway current = getGiveaway(id);
		if (current.status() != GiveawayStatus.DRAFT) {
			throw new ConflictException("Can only update giveaways in DRAFT status");
		}
		Giveaway updated = new Giveaway(
				current.id(),
				request.title() == null || request.title().isBlank() ? current.title() : request.title().trim(),
				request.description() == null ? current.description() : request.description().trim(),
				current.status(),
				current.entryCount(),
				current.maxEntries(),
				current.createdAt(),
				current.openedAt(),
				current.closedAt(),
				current.drawnAt(),
				current.winnerIds());
		return giveawayStore.save(updated);
	}

	public void deleteGiveaway(String id) {
		Giveaway giveaway = getGiveaway(id);
		if (giveaway.status() != GiveawayStatus.DRAFT && giveaway.status() != GiveawayStatus.CANCELLED) {
			throw new ConflictException("Can only delete giveaways in DRAFT or CANCELLED status");
		}
		entryStore.deleteByGiveawayId(id);
		giveawayStore.delete(id);
	}

	public Giveaway openGiveaway(String id) {
		Giveaway giveaway = getGiveaway(id);
		if (giveaway.status() != GiveawayStatus.DRAFT) {
			throw new ConflictException("Can only open giveaways in DRAFT status");
		}
		Instant now = Instant.now(clock);
		Giveaway opened = new Giveaway(
				giveaway.id(),
				giveaway.title(),
				giveaway.description(),
				GiveawayStatus.OPEN,
				giveaway.entryCount(),
				giveaway.maxEntries(),
				giveaway.createdAt(),
				now,
				null,
				null,
				giveaway.winnerIds());
		return giveawayStore.save(opened);
	}

	public Giveaway closeGiveaway(String id) {
		Giveaway giveaway = getGiveaway(id);
		if (giveaway.status() != GiveawayStatus.OPEN) {
			throw new ConflictException("Can only close giveaways in OPEN status");
		}
		Instant now = Instant.now(clock);
		Giveaway closed = new Giveaway(
				giveaway.id(),
				giveaway.title(),
				giveaway.description(),
				GiveawayStatus.CLOSED,
				giveaway.entryCount(),
				giveaway.maxEntries(),
				giveaway.createdAt(),
				giveaway.openedAt(),
				now,
				null,
				giveaway.winnerIds());
		return giveawayStore.save(closed);
	}

	public GiveawayEntry enterGiveaway(String giveawayId, EnterGiveawayRequest request) {
		Giveaway giveaway = getGiveaway(giveawayId);
		if (giveaway.status() != GiveawayStatus.OPEN) {
			throw new ConflictException("Giveaway is not open for entries");
		}
		if (giveaway.entryCount() >= giveaway.maxEntries()) {
			throw new ConflictException("Giveaway has reached maximum entries");
		}
		String participantName = request.participantName().trim();
		if (entryStore.existsByGiveawayIdAndParticipantName(giveawayId, participantName)) {
			throw new ConflictException("Participant already entered this giveaway");
		}
		Instant now = Instant.now(clock);
		GiveawayEntry entry = new GiveawayEntry(
				idGenerator.newId(),
				giveawayId,
				participantName,
				now,
				false,
				true);
		entryStore.save(entry);
		Giveaway updated = new Giveaway(
				giveaway.id(),
				giveaway.title(),
				giveaway.description(),
				giveaway.status(),
				giveaway.entryCount() + 1,
				giveaway.maxEntries(),
				giveaway.createdAt(),
				giveaway.openedAt(),
				giveaway.closedAt(),
				giveaway.drawnAt(),
				giveaway.winnerIds());
		giveawayStore.save(updated);
		return entry;
	}

	public Giveaway drawWinners(String giveawayId, int winnerCount) {
		Giveaway giveaway = getGiveaway(giveawayId);
		if (giveaway.status() != GiveawayStatus.CLOSED) {
			throw new ConflictException("Can only draw winners when giveaway is CLOSED");
		}
		List<GiveawayEntry> entries = entryStore.findByGiveawayId(giveawayId);
		if (entries.isEmpty()) {
			throw new ConflictException("No entries to draw from");
		}
		int actualWinnerCount = Math.min(winnerCount <= 0 ? DEFAULT_WINNER_COUNT : winnerCount, entries.size());

		// Set status to DRAWING
		Instant now = Instant.now(clock);
		Giveaway drawing = new Giveaway(
				giveaway.id(),
				giveaway.title(),
				giveaway.description(),
				GiveawayStatus.DRAWING,
				giveaway.entryCount(),
				giveaway.maxEntries(),
				giveaway.createdAt(),
				giveaway.openedAt(),
				giveaway.closedAt(),
				null,
				giveaway.winnerIds());
		giveawayStore.save(drawing);

		// Shuffle and pick winners
		List<GiveawayEntry> shuffled = new ArrayList<>(entries);
		Collections.shuffle(shuffled);
		List<GiveawayEntry> selected = shuffled.subList(0, actualWinnerCount);
		List<String> winnerIds = selected.stream().map(GiveawayEntry::id).toList();

		// Update entries with selection status
		List<GiveawayEntry> allEntries = new ArrayList<>(entries);
		List<GiveawayEntry> updatedEntries = allEntries.stream()
				.map(e -> new GiveawayEntry(
						e.id(),
						e.giveawayId(),
						e.participantName(),
						e.enteredAt(),
						winnerIds.contains(e.id()),
						e.eligible()))
				.toList();
		entryStore.replaceAllForGiveaway(giveawayId, updatedEntries);

		// Set status to COMPLETED
		Giveaway completed = new Giveaway(
				giveaway.id(),
				giveaway.title(),
				giveaway.description(),
				GiveawayStatus.COMPLETED,
				giveaway.entryCount(),
				giveaway.maxEntries(),
				giveaway.createdAt(),
				giveaway.openedAt(),
				giveaway.closedAt(),
				now,
				winnerIds);
		return giveawayStore.save(completed);
	}

	public GiveawayResultsResponse getResults(String giveawayId) {
		Giveaway giveaway = getGiveaway(giveawayId);
		List<GiveawayEntry> entries = entryStore.findByGiveawayId(giveawayId);
		return GiveawayResultsResponse.from(giveaway, entries);
	}

	public List<GiveawayEntry> getEntries(String giveawayId) {
		getGiveaway(giveawayId); // validate existence
		return entryStore.findByGiveawayId(giveawayId);
	}

	public GiveawayStatsResponse getStats() {
		List<Giveaway> all = giveawayStore.findAll();
		Map<String, Integer> entriesPerGiveaway = entryStore.countAll();
		return new GiveawayStatsResponse(
				all.size(),
				(int) all.stream().filter(g -> g.status() == GiveawayStatus.DRAFT).count(),
				(int) all.stream().filter(g -> g.status() == GiveawayStatus.OPEN).count(),
				(int) all.stream().filter(g -> g.status() == GiveawayStatus.CLOSED).count(),
				(int) all.stream().filter(g -> g.status() == GiveawayStatus.COMPLETED).count(),
				(int) all.stream().filter(g -> g.status() == GiveawayStatus.CANCELLED).count(),
				entryStore.totalCount(),
				entriesPerGiveaway);
	}
}
