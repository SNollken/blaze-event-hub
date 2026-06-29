package com.nollen.blaze.overlays.runtime;

import java.util.List;
import java.util.Optional;

public interface RuntimeOverlayConfigStore {

	RuntimeOverlayConfig save(RuntimeOverlayConfig config);

	Optional<RuntimeOverlayConfig> findById(String id);

	List<RuntimeOverlayConfig> findAll();

	List<RuntimeOverlayConfig> findByType(RuntimeOverlayType type);

	void deleteById(String id);

	long count();
}
