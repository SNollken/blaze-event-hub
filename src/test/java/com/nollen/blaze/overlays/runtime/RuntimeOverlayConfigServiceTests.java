package com.nollen.blaze.overlays.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nollen.blaze.common.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeOverlayConfigServiceTests {

    private RuntimeOverlayConfigService service;
    private RuntimeOverlayConfigStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryRuntimeOverlayConfigStore();
        service = new RuntimeOverlayConfigService(store);
    }

    @Test
    void seedsDefaultConfigsOnStartup() {
        assertThat(service.listAll()).hasSize(3);
        assertThat(service.listAll().stream().map(RuntimeOverlayConfig::type).toList())
                .containsExactlyInAnyOrder(RuntimeOverlayType.ALERT, RuntimeOverlayType.GIVEAWAY, RuntimeOverlayType.EVENTS);
    }

    @Test
    void defaultConfigsAreEnabled() {
        for (RuntimeOverlayConfig config : service.listAll()) {
            assertThat(config.enabled()).isTrue();
            assertThat(config.refreshIntervalMs()).isEqualTo(3000);
            assertThat(config.opacity()).isEqualTo(1.0);
        }
    }

    @Test
    void getsConfigById() {
        RuntimeOverlayConfig alert = service.listAll().stream()
                .filter(c -> c.type() == RuntimeOverlayType.ALERT)
                .findFirst().orElseThrow();
        RuntimeOverlayConfig fetched = service.getById(alert.id());
        assertThat(fetched.type()).isEqualTo(RuntimeOverlayType.ALERT);
    }

    @Test
    void throwsNotFoundExceptionForInvalidId() {
        assertThatThrownBy(() -> service.getById("nonexistent"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsNewConfig() {
        CreateRuntimeOverlayConfigRequest request = new CreateRuntimeOverlayConfigRequest(
                RuntimeOverlayType.ALERT, "Custom Alert", true, 5000L, ".custom{}", 10, 20, 300, 150, 0.8);
        RuntimeOverlayConfig created = service.create(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Custom Alert");
        assertThat(created.refreshIntervalMs()).isEqualTo(5000);
        assertThat(created.opacity()).isEqualTo(0.8);
        assertThat(created.positionX()).isEqualTo(10);
    }

    @Test
    void createsWithDefaultsWhenNulls() {
        CreateRuntimeOverlayConfigRequest request = new CreateRuntimeOverlayConfigRequest(
                RuntimeOverlayType.GIVEAWAY, "Test", null, null, null, null, null, null, null, null);
        RuntimeOverlayConfig created = service.create(request);
        assertThat(created.enabled()).isTrue();
        assertThat(created.refreshIntervalMs()).isEqualTo(3000);
        assertThat(created.opacity()).isEqualTo(1.0);
        assertThat(created.positionX()).isEqualTo(0);
    }

    @Test
    void updatesConfig() {
        RuntimeOverlayConfig existing = service.listAll().get(0);
        UpdateRuntimeOverlayConfigRequest request = new UpdateRuntimeOverlayConfigRequest(
                null, "Updated Name", false, 10000L, null, null, null, null, null, 0.5);
        RuntimeOverlayConfig updated = service.update(existing.id(), request);
        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.refreshIntervalMs()).isEqualTo(10000);
        assertThat(updated.opacity()).isEqualTo(0.5);
        assertThat(updated.type()).isEqualTo(existing.type());
    }

    @Test
    void deletesConfig() {
        RuntimeOverlayConfig existing = service.listAll().get(0);
        int sizeBefore = service.listAll().size();
        service.delete(existing.id());
        assertThat(service.listAll()).hasSize(sizeBefore - 1);
    }

    @Test
    void deleteThrowsForInvalidId() {
        assertThatThrownBy(() -> service.delete("nonexistent"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findConfigsByType() {
        var alerts = store.findByType(RuntimeOverlayType.ALERT);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).type()).isEqualTo(RuntimeOverlayType.ALERT);
    }
}
