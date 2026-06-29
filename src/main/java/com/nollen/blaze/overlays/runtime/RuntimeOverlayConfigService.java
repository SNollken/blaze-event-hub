package com.nollen.blaze.overlays.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.nollen.blaze.common.NotFoundException;

@Service
public class RuntimeOverlayConfigService {

	private final RuntimeOverlayConfigStore store;
	private final Map<RuntimeOverlayType, CopyOnWriteArrayList<Consumer<RuntimeOverlayEvent>>> listeners = new ConcurrentHashMap<>();

	public RuntimeOverlayConfigService(RuntimeOverlayConfigStore store) {
		this.store = store;
		seedDefaults();
	}

	private void seedDefaults() {
		for (RuntimeOverlayType type : RuntimeOverlayType.values()) {
			if (store.findByType(type).isEmpty()) {
				RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(type);
				store.save(config.withId(generateId()));
			}
		}
	}

	public List<RuntimeOverlayConfig> listAll() {
		return store.findAll();
	}

	public RuntimeOverlayConfig getById(String id) {
		return store.findById(id)
				.orElseThrow(() -> new NotFoundException("Overlay config not found"));
	}

	public RuntimeOverlayConfig create(CreateRuntimeOverlayConfigRequest request) {
		String id = generateId();
		RuntimeOverlayConfig config = new RuntimeOverlayConfig(
				id,
				request.type(),
				request.name(),
				request.enabled() == null ? true : request.enabled(),
				request.refreshIntervalMs() == null ? 3000 : request.refreshIntervalMs(),
				request.customCss() == null ? "" : request.customCss(),
				request.positionX() == null ? 0 : request.positionX(),
				request.positionY() == null ? 0 : request.positionY(),
				request.positionWidth() == null ? 400 : request.positionWidth(),
				request.positionHeight() == null ? 200 : request.positionHeight(),
				request.opacity() == null ? 1.0 : request.opacity());
		return store.save(config);
	}

	public RuntimeOverlayConfig update(String id, UpdateRuntimeOverlayConfigRequest request) {
		RuntimeOverlayConfig current = getById(id);
		RuntimeOverlayConfig updated = new RuntimeOverlayConfig(
				current.id(),
				request.type() == null ? current.type() : request.type(),
				request.name() == null ? current.name() : request.name(),
				request.enabled() == null ? current.enabled() : request.enabled(),
				request.refreshIntervalMs() == null ? current.refreshIntervalMs() : request.refreshIntervalMs(),
				request.customCss() == null ? current.customCss() : request.customCss(),
				request.positionX() == null ? current.positionX() : request.positionX(),
				request.positionY() == null ? current.positionY() : request.positionY(),
				request.positionWidth() == null ? current.positionWidth() : request.positionWidth(),
				request.positionHeight() == null ? current.positionHeight() : request.positionHeight(),
				request.opacity() == null ? current.opacity() : request.opacity());
		return store.save(updated);
	}

	public void delete(String id) {
		getById(id);
		store.deleteById(id);
	}

	public void addListener(RuntimeOverlayType type, Consumer<RuntimeOverlayEvent> listener) {
		listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
	}

	public void removeListener(RuntimeOverlayType type, Consumer<RuntimeOverlayEvent> listener) {
		List<Consumer<RuntimeOverlayEvent>> list = listeners.get(type);
		if (list != null) {
			list.remove(listener);
		}
	}

	public void fireEvent(RuntimeOverlayEvent event) {
		List<Consumer<RuntimeOverlayEvent>> list = listeners.get(event.type());
		if (list != null) {
			for (Consumer<RuntimeOverlayEvent> listener : list) {
				listener.accept(event);
			}
		}
	}

	private String generateId() {
		return UUID.randomUUID().toString();
	}
}
