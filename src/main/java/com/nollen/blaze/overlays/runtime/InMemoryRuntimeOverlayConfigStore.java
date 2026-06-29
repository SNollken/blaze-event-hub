package com.nollen.blaze.overlays.runtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRuntimeOverlayConfigStore implements RuntimeOverlayConfigStore {

    private final ConcurrentHashMap<String, RuntimeOverlayConfig> configs = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final RowMapper<RuntimeOverlayConfig> mapper = (rs, rowNum) -> new RuntimeOverlayConfig(
            rs.getString("id"),
            RuntimeOverlayType.valueOf(rs.getString("type")),
            rs.getString("name"),
            rs.getBoolean("enabled"),
            rs.getLong("refresh_interval_ms"),
            rs.getString("custom_css"),
            rs.getInt("position_x"),
            rs.getInt("position_y"),
            rs.getInt("position_width"),
            rs.getInt("position_height"),
            rs.getDouble("opacity"));

    public InMemoryRuntimeOverlayConfigStore() {
        this.jdbc = null;
    }

    @Autowired
    public InMemoryRuntimeOverlayConfigStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RuntimeOverlayConfig save(RuntimeOverlayConfig config) {
        if (jdbc != null) {
            jdbc.update("""
                    MERGE INTO runtime_overlay_configs KEY(id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    config.id(),
                    config.type().name(),
                    config.name(),
                    config.enabled(),
                    config.refreshIntervalMs(),
                    config.customCss(),
                    config.positionX(),
                    config.positionY(),
                    config.positionWidth(),
                    config.positionHeight(),
                    config.opacity());
            return config;
        }
        configs.put(config.id(), config);
        return config;
    }

    @Override
    public Optional<RuntimeOverlayConfig> findById(String id) {
        if (jdbc != null) {
            return jdbc.query("SELECT * FROM runtime_overlay_configs WHERE id = ?", mapper, id).stream().findFirst();
        }
        return Optional.ofNullable(configs.get(id));
    }

    @Override
    public List<RuntimeOverlayConfig> findAll() {
        if (jdbc != null) {
            return jdbc.query("SELECT * FROM runtime_overlay_configs ORDER BY id", mapper);
        }
        return new ArrayList<>(configs.values());
    }

    @Override
    public List<RuntimeOverlayConfig> findByType(RuntimeOverlayType type) {
        if (jdbc != null) {
            return jdbc.query("SELECT * FROM runtime_overlay_configs WHERE type = ? ORDER BY id", mapper, type.name());
        }
        return configs.values().stream()
                .filter(config -> config.type() == type)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        if (jdbc != null) {
            jdbc.update("DELETE FROM runtime_overlay_configs WHERE id = ?", id);
            return;
        }
        configs.remove(id);
    }

    @Override
    public long count() {
        if (jdbc != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM runtime_overlay_configs", Long.class);
            return count == null ? 0 : count;
        }
        return configs.size();
    }
}
