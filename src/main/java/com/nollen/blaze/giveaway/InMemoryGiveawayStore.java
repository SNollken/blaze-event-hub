package com.nollen.blaze.giveaway;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.nollen.blaze.common.JsonData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGiveawayStore {

	private final ConcurrentHashMap<String, Giveaway> giveaways = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<Giveaway> mapper = (rs, rowNum) -> new Giveaway(
			rs.getString("id"),
			rs.getString("title"),
			rs.getString("description"),
			GiveawayStatus.valueOf(rs.getString("status")),
			rs.getInt("entry_count"),
			rs.getInt("max_entries"),
			rs.getTimestamp("created_at").toInstant(),
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
			jdbc.update("""
					MERGE INTO giveaways KEY(id)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					giveaway.id(),
					giveaway.title(),
					giveaway.description(),
					giveaway.status().name(),
					giveaway.entryCount(),
					giveaway.maxEntries(),
					giveaway.createdAt(),
					giveaway.openedAt(),
					giveaway.closedAt(),
					giveaway.drawnAt(),
					JsonData.write(giveaway.winnerIds()));
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
				.sorted(Comparator.comparing(Giveaway::createdAt))
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
