package com.nollen.blaze.overlays;

import java.util.List;
import java.util.Optional;

public interface OverlayRepository {

	OverlayProfile saveProfile(OverlayProfile profile);

	List<OverlayProfile> listProfiles();

	Optional<OverlayProfile> findProfile(String profileId);

	void deleteProfile(String profileId);

	Overlay saveOverlay(Overlay overlay);

	List<Overlay> listOverlays(String profileId);

	Optional<Overlay> findOverlay(String overlayId);

	Optional<Overlay> findByPublicToken(String publicToken);

	void deleteOverlay(String overlayId);

	long countProfiles();

	long countOverlays();

	List<Overlay> listAllOverlays();
}
