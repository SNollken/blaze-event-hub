package com.nollen.blaze.giveaway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGiveawayEntryStore {

	private final ConcurrentHashMap<String, CopyOnWriteArrayList<GiveawayEntry>> entriesByGiveaway = new ConcurrentHashMap<>();

	public GiveawayEntry save(GiveawayEntry entry) {
		entriesByGiveaway
				.computeIfAbsent(entry.giveawayId(), k -> new CopyOnWriteArrayList<>())
				.add(entry);
		return entry;
	}

	public List<GiveawayEntry> findByGiveawayId(String giveawayId) {
		CopyOnWriteArrayList<GiveawayEntry> list = entriesByGiveaway.get(giveawayId);
		if (list == null) {
			return List.of();
		}
		return list.stream()
				.sorted(Comparator.comparing(GiveawayEntry::enteredAt))
				.toList();
	}

	public boolean existsByGiveawayIdAndParticipantName(String giveawayId, String participantName) {
		CopyOnWriteArrayList<GiveawayEntry> list = entriesByGiveaway.get(giveawayId);
		if (list == null) {
			return false;
		}
		return list.stream()
				.anyMatch(e -> e.participantName().equalsIgnoreCase(participantName));
	}

	public int countByGiveawayId(String giveawayId) {
		CopyOnWriteArrayList<GiveawayEntry> list = entriesByGiveaway.get(giveawayId);
		return list == null ? 0 : list.size();
	}

	public void deleteByGiveawayId(String giveawayId) {
		entriesByGiveaway.remove(giveawayId);
	}

	public Map<String, Integer> countAll() {
		return entriesByGiveaway.entrySet().stream()
				.collect(java.util.stream.Collectors.toMap(
						Map.Entry::getKey,
						e -> e.getValue().size()));
	}

	public int totalCount() {
		return entriesByGiveaway.values().stream()
				.mapToInt(List::size)
				.sum();
	}

	public List<GiveawayEntry> findAll() {
		return entriesByGiveaway.values().stream()
				.flatMap(List::stream)
				.toList();
	}

	public void replaceAllForGiveaway(String giveawayId, List<GiveawayEntry> updatedEntries) {
		entriesByGiveaway.put(giveawayId, new CopyOnWriteArrayList<>(updatedEntries));
	}
}
