package com.nollen.blaze.giveaway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGiveawayEntryStore {

	private final ConcurrentHashMap<String, CopyOnWriteArrayList<GiveawayEntry>> entriesByGiveaway = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<GiveawayEntry> mapper = (rs, rowNum) -> new GiveawayEntry(
			rs.getString("id"),
			rs.getString("giveaway_id"),
			rs.getString("participant_name"),
			rs.getTimestamp("entered_at").toInstant(),
			rs.getBoolean("selected"),
			rs.getBoolean("eligible"));

	public InMemoryGiveawayEntryStore() {
		this.jdbc = null;
	}

	@Autowired
	public InMemoryGiveawayEntryStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public GiveawayEntry save(GiveawayEntry entry) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO giveaway_entries KEY(id)
					VALUES (?, ?, ?, ?, ?, ?)
					""",
					entry.id(),
					entry.giveawayId(),
					entry.participantName(),
					entry.enteredAt(),
					entry.selected(),
					entry.eligible());
			return entry;
		}
		entriesByGiveaway
				.computeIfAbsent(entry.giveawayId(), k -> new CopyOnWriteArrayList<>())
				.add(entry);
		return entry;
	}

	public List<GiveawayEntry> findByGiveawayId(String giveawayId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM giveaway_entries WHERE giveaway_id = ? ORDER BY entered_at",
					mapper, giveawayId);
		}
		CopyOnWriteArrayList<GiveawayEntry> list = entriesByGiveaway.get(giveawayId);
		if (list == null) {
			return List.of();
		}
		return list.stream()
				.sorted(Comparator.comparing(GiveawayEntry::enteredAt))
				.toList();
	}

	public boolean existsByGiveawayIdAndParticipantName(String giveawayId, String participantName) {
		if (jdbc != null) {
			Integer count = jdbc.queryForObject("""
					SELECT COUNT(*) FROM giveaway_entries
					WHERE giveaway_id = ? AND LOWER(participant_name) = LOWER(?)
					""", Integer.class, giveawayId, participantName);
			return count != null && count > 0;
		}
		CopyOnWriteArrayList<GiveawayEntry> list = entriesByGiveaway.get(giveawayId);
		if (list == null) {
			return false;
		}
		return list.stream()
				.anyMatch(e -> e.participantName().equalsIgnoreCase(participantName));
	}

	public int countByGiveawayId(String giveawayId) {
		if (jdbc != null) {
			Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM giveaway_entries WHERE giveaway_id = ?",
					Integer.class, giveawayId);
			return count == null ? 0 : count;
		}
		CopyOnWriteArrayList<GiveawayEntry> list = entriesByGiveaway.get(giveawayId);
		return list == null ? 0 : list.size();
	}

	public void deleteByGiveawayId(String giveawayId) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM giveaway_entries WHERE giveaway_id = ?", giveawayId);
			return;
		}
		entriesByGiveaway.remove(giveawayId);
	}

	public Map<String, Integer> countAll() {
		if (jdbc != null) {
			return jdbc.query("""
					SELECT giveaway_id, COUNT(*) AS total
					FROM giveaway_entries
					GROUP BY giveaway_id
					""", (rs, rowNum) -> Map.entry(rs.getString("giveaway_id"), rs.getInt("total"))).stream()
					.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}
		return entriesByGiveaway.entrySet().stream()
				.collect(java.util.stream.Collectors.toMap(
						Map.Entry::getKey,
						e -> e.getValue().size()));
	}

	public int totalCount() {
		if (jdbc != null) {
			Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM giveaway_entries", Integer.class);
			return count == null ? 0 : count;
		}
		return entriesByGiveaway.values().stream()
				.mapToInt(List::size)
				.sum();
	}

	public List<GiveawayEntry> findAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM giveaway_entries ORDER BY entered_at", mapper);
		}
		return entriesByGiveaway.values().stream()
				.flatMap(List::stream)
				.toList();
	}

	public void replaceAllForGiveaway(String giveawayId, List<GiveawayEntry> updatedEntries) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM giveaway_entries WHERE giveaway_id = ?", giveawayId);
			for (GiveawayEntry entry : updatedEntries) {
				save(entry);
			}
			return;
		}
		entriesByGiveaway.put(giveawayId, new CopyOnWriteArrayList<>(updatedEntries));
	}
}
