package com.blaze.eventhub.giveaway;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.blaze.eventhub.common.JsonData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGiveawayStore {

	private static final Comparator<Giveaway> BY_TIME = Comparator.comparing(Giveaway::createdAt);

	private final ConcurrentHashMap<String, Giveaway> giveaways = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<Giveaway> mapper = (rs, rowNum) -> new Giveaway(
			rs.getString("id"),
			rs.getString("title"),
			rs.getString("description"),
			GiveawayStatus.valueOf(rs.getString("status")),
			rs.getInt("entry_count"),
			rs.getInt("max_entries"),
			rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.EPOCH,
			rs.getTimestamp("opened_at") == null ? null : rs.getTimestamp("opened_at").toInstant(),
			rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant(),
			rs.getTimestamp("drawn_at") == null ? null : rs.getTimestamp("drawn_at").toInstant(),
			JsonData.readStringList(rs.getString("winner_ids")));

	public InMemoryGiveawayStore() {
		this.jdbc = null;
	}

	@Autowired
	public InMemoryGiveawayStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Giveaway save(Giveaway giveaway) {
		if (jdbc != null) {
			Timestamp createdAt = Timestamp.from(giveaway.createdAt());
			Timestamp openedAt = giveaway.openedAt() != null ? Timestamp.from(giveaway.openedAt()) : null;
			Timestamp closedAt = giveaway.closedAt() != null ? Timestamp.from(giveaway.closedAt()) : null;
			Timestamp drawnAt = giveaway.drawnAt() != null ? Timestamp.from(giveaway.drawnAt()) : null;
			int updated = jdbc.update(
					"UPDATE giveaways SET title = ?, description = ?, status = ?, entry_count = ?, max_entries = ?, created_at = ?, opened_at = ?, closed_at = ?, drawn_at = ?, winner_ids = ? WHERE id = ?",
					giveaway.title(), giveaway.description(), giveaway.status().name(), giveaway.entryCount(), giveaway.maxEntries(), createdAt, openedAt, closedAt, drawnAt, JsonData.write(giveaway.winnerIds()), giveaway.id());
			if (updated == 0) {
				jdbc.update(
					"INSERT INTO giveaways (title, description, status, entry_count, max_entries, created_at, opened_at, closed_at, drawn_at, winner_ids, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					giveaway.title(), giveaway.description(), giveaway.status().name(), giveaway.entryCount(), giveaway.maxEntries(), createdAt, openedAt, closedAt, drawnAt, JsonData.write(giveaway.winnerIds()), giveaway.id());
			}

			return giveaway;
		}
		giveaways.put(giveaway.id(), giveaway);
		return giveaway;
	}

	public Optional<Giveaway> findById(String id) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM giveaways WHERE id = ?", mapper, id).stream().findFirst();
		}
		return Optional.ofNullable(giveaways.get(id));
	}

	public List<Giveaway> findAll() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM giveaways ORDER BY created_at", mapper);
		}
		return giveaways.values().stream()
				.sorted(BY_TIME)
				.toList();
	}

	public void delete(String id) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM giveaways WHERE id = ?", id);
			return;
		}
		giveaways.remove(id);
	}

	public int count() {
		if (jdbc != null) {
			Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM giveaways", Integer.class);
			return count == null ? 0 : count;
		}
		return giveaways.size();
	}
}
