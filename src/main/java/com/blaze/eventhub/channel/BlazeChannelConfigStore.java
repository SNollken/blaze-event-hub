package com.blaze.eventhub.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BlazeChannelConfigStore {

	private final ConcurrentHashMap<String, BlazeChannelConfig> channels = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<BlazeChannelConfig> mapper = (rs, rowNum) -> new BlazeChannelConfig(
			rs.getString("id"),
			rs.getString("name"),
			rs.getString("channel_id"),
			rs.getString("platform"),
			rs.getBoolean("monitored"));

	public BlazeChannelConfigStore() {
		this.jdbc = null;
	}

	@Autowired
	public BlazeChannelConfigStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void save(BlazeChannelConfig config) {
		if (jdbc != null) {
			int updated = jdbc.update(
				"UPDATE blaze_channels SET name = ?, channel_id = ?, platform = ?, monitored = ? WHERE id = ?",
				config.name(), config.channelId(), config.platform(), config.monitored(), config.id());
			if (updated == 0) {
				jdbc.update(
					"INSERT INTO blaze_channels (name, channel_id, platform, monitored, id) VALUES (?, ?, ?, ?, ?)",
					config.name(), config.channelId(), config.platform(), config.monitored(), config.id());
			}

			return;
		}
		channels.put(config.id(), config);
	}

	public BlazeChannelConfig findById(String id) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM blaze_channels WHERE id = ?", mapper, id).stream()
					.findFirst()
					.orElse(null);
		}
		return channels.get(id);
	}

	public List<BlazeChannelConfig> list() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM blaze_channels ORDER BY name", mapper);
		}
		return new ArrayList<>(channels.values());
	}

	public boolean deleteById(String id) {
		if (jdbc != null) {
			return jdbc.update("DELETE FROM blaze_channels WHERE id = ?", id) > 0;
		}
		return channels.remove(id) != null;
	}

	public long count() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM blaze_channels", Long.class);
			return count == null ? 0 : count;
		}
		return channels.size();
	}
}
