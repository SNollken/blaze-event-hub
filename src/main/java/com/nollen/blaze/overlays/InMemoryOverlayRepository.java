package com.nollen.blaze.overlays;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nollen.blaze.common.JsonData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOverlayRepository implements OverlayRepository {

	private static final TypeReference<List<OverlayLayer>> LAYERS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<OverlayAsset>> ASSETS_TYPE = new TypeReference<>() {
	};

	private final ConcurrentHashMap<String, OverlayProfile> profiles = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Overlay> overlays = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;
	private final RowMapper<OverlayProfile> profileMapper = (rs, rowNum) -> new OverlayProfile(
			rs.getString("id"),
			rs.getString("name"),
			rs.getString("description"),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("updated_at").toInstant());
	private final RowMapper<Overlay> overlayMapper = (rs, rowNum) -> new Overlay(
			rs.getString("id"),
			rs.getString("profile_id"),
			rs.getString("name"),
			rs.getString("type"),
			rs.getString("public_token"),
			rs.getBoolean("enabled"),
			JsonData.read(rs.getString("config"), new TypeReference<OverlayConfig>() {
			}, OverlayConfig.defaultConfig()),
			JsonData.read(rs.getString("layers"), LAYERS_TYPE, List.of()),
			JsonData.read(rs.getString("assets"), ASSETS_TYPE, List.of()),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("updated_at").toInstant());

	public InMemoryOverlayRepository() {
		this.jdbc = null;
	}

	@Autowired
	public InMemoryOverlayRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public OverlayProfile saveProfile(OverlayProfile profile) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO overlay_profiles KEY(id)
					VALUES (?, ?, ?, ?, ?)
					""",
					profile.id(),
					profile.name(),
					profile.description(),
					profile.createdAt(),
					profile.updatedAt());
			return profile;
		}
		profiles.put(profile.id(), profile);
		return profile;
	}

	@Override
	public List<OverlayProfile> listProfiles() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM overlay_profiles ORDER BY created_at", profileMapper);
		}
		return profiles.values().stream()
				.sorted(Comparator.comparing(OverlayProfile::createdAt))
				.toList();
	}

	@Override
	public Optional<OverlayProfile> findProfile(String profileId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM overlay_profiles WHERE id = ?", profileMapper, profileId)
					.stream().findFirst();
		}
		return Optional.ofNullable(profiles.get(profileId));
	}

	@Override
	public void deleteProfile(String profileId) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM overlays WHERE profile_id = ?", profileId);
			jdbc.update("DELETE FROM overlay_profiles WHERE id = ?", profileId);
			return;
		}
		profiles.remove(profileId);
		overlays.values().removeIf(overlay -> overlay.profileId().equals(profileId));
	}

	@Override
	public Overlay saveOverlay(Overlay overlay) {
		if (jdbc != null) {
			jdbc.update("""
					MERGE INTO overlays KEY(id)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					overlay.id(),
					overlay.profileId(),
					overlay.name(),
					overlay.type(),
					overlay.publicToken(),
					overlay.enabled(),
					JsonData.write(overlay.config()),
					JsonData.write(overlay.layers()),
					JsonData.write(overlay.assets()),
					overlay.createdAt(),
					overlay.updatedAt());
			return overlay;
		}
		overlays.put(overlay.id(), overlay);
		return overlay;
	}

	@Override
	public List<Overlay> listOverlays(String profileId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM overlays WHERE profile_id = ? ORDER BY created_at", overlayMapper, profileId);
		}
		return overlays.values().stream()
				.filter(overlay -> overlay.profileId().equals(profileId))
				.sorted(Comparator.comparing(Overlay::createdAt))
				.toList();
	}

	@Override
	public Optional<Overlay> findOverlay(String overlayId) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM overlays WHERE id = ?", overlayMapper, overlayId)
					.stream().findFirst();
		}
		return Optional.ofNullable(overlays.get(overlayId));
	}

	@Override
	public Optional<Overlay> findByPublicToken(String publicToken) {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM overlays WHERE public_token = ?", overlayMapper, publicToken)
					.stream().findFirst();
		}
		return overlays.values().stream()
				.filter(overlay -> overlay.publicToken().equals(publicToken))
				.findFirst();
	}

	@Override
	public void deleteOverlay(String overlayId) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM overlays WHERE id = ?", overlayId);
			return;
		}
		overlays.remove(overlayId);
	}

	@Override
	public long countProfiles() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM overlay_profiles", Long.class);
			return count == null ? 0 : count;
		}
		return profiles.size();
	}

	@Override
	public long countOverlays() {
		if (jdbc != null) {
			Long count = jdbc.queryForObject("SELECT COUNT(*) FROM overlays", Long.class);
			return count == null ? 0 : count;
		}
		return overlays.size();
	}

	@Override
	public List<Overlay> listAllOverlays() {
		if (jdbc != null) {
			return jdbc.query("SELECT * FROM overlays ORDER BY created_at", overlayMapper);
		}
		return new ArrayList<>(overlays.values());
	}
}
