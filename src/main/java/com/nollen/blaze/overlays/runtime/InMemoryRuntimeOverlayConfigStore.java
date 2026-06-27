package com.nollen.blaze.overlays.runtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRuntimeOverlayConfigStore implements RuntimeOverlayConfigStore {

    private final ConcurrentHashMap<String, RuntimeOverlayConfig> configs = new ConcurrentHashMap<>();

    @Override
    public RuntimeOverlayConfig save(RuntimeOverlayConfig config) {
        configs.put(config.id(), config);
        return config;
    }

    @Override
    public Optional<RuntimeOverlayConfig> findById(String id) {
        return Optional.ofNullable(configs.get(id));
    }

    @Override
    public List<RuntimeOverlayConfig> findAll() {
        return new ArrayList<>(configs.values());
    }

    @Override
    public List<RuntimeOverlayConfig> findByType(RuntimeOverlayType type) {
        return configs.values().stream()
                .filter(config -> config.type() == type)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        configs.remove(id);
    }

    @Override
    public long count() {
        return configs.size();
    }
}