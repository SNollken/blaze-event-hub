package com.nollen.blaze.overlays;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOverlayRepository implements OverlayRepository {

	private final ConcurrentHashMap<String, OverlayProfile> profiles = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Overlay> overlays = new ConcurrentHashMap<>();

	@Override
	public OverlayProfile saveProfile(OverlayProfile profile) {
		profiles.put(profile.id(), profile);
		return profile;
	}

	@Override
	public List<OverlayProfile> listProfiles() {
		return profiles.values().stream()
				.sorted(Comparator.comparing(OverlayProfile::createdAt))
				.toList();
	}

	@Override
	public Optional<OverlayProfile> findProfile(String profileId) {
		return Optional.ofNullable(profiles.get(profileId));
	}

	@Override
	public void deleteProfile(String profileId) {
		profiles.remove(profileId);
		overlays.values().removeIf(overlay -> overlay.profileId().equals(profileId));
	}

	@Override
	public Overlay saveOverlay(Overlay overlay) {
		overlays.put(overlay.id(), overlay);
		return overlay;
	}

	@Override
	public List<Overlay> listOverlays(String profileId) {
		return overlays.values().stream()
				.filter(overlay -> overlay.profileId().equals(profileId))
				.sorted(Comparator.comparing(Overlay::createdAt))
				.toList();
	}

	@Override
	public Optional<Overlay> findOverlay(String overlayId) {
		return Optional.ofNullable(overlays.get(overlayId));
	}

	@Override
	public Optional<Overlay> findByPublicToken(String publicToken) {
		return overlays.values().stream()
				.filter(overlay -> overlay.publicToken().equals(publicToken))
				.findFirst();
	}

	@Override
	public void deleteOverlay(String overlayId) {
		overlays.remove(overlayId);
	}

	@Override
	public long countProfiles() {
		return profiles.size();
	}

	@Override
	public long countOverlays() {
		return overlays.size();
	}

	@Override
	public List<Overlay> listAllOverlays() {
		return new ArrayList<>(overlays.values());
	}
}
