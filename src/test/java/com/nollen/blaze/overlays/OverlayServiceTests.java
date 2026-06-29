package com.nollen.blaze.overlays;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlayServiceTests {

	private OverlayService service;

	@BeforeEach
	void setUp() {
		service = new OverlayService(
				new InMemoryOverlayRepository(),
				new InMemoryOverlayAssetStorage(),
				new IdGenerator(),
				Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void createsProfileOverlayAndLayerWithPublicManifest() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", "desc"));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));
		OverlayLayer layer = service.createLayer(overlay.id(), new CreateOverlayLayerRequest(
				OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, 0.9, "NollenBlaze", null, Map.of("color", "#fff")));

		assertThat(service.listOverlays(profile.id())).hasSize(1);
		assertThat(overlay.publicToken()).isNotBlank();
		assertThat(layer.overlayId()).isEqualTo(overlay.id());
		assertThat(service.manifest(overlay.publicToken()).layers()).hasSize(1);
	}

	@Test
	void updateOverlayPreservesPublicToken() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));

		Overlay updated = service.updateOverlay(overlay.id(), new UpdateOverlayRequest("Overlay Renamed", "alert", false, null));

		assertThat(updated.publicToken()).isEqualTo(overlay.publicToken());
		assertThat(updated.enabled()).isFalse();
	}

	@Test
	void invalidTokenReturnsNotFound() {
		assertThatThrownBy(() -> service.manifest("invalid-token"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void disabledOverlayReturnsSafeEmptyManifest() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", false, null));
		service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, 1.0, "Hidden", null, null));

		OverlayManifestResponse manifest = service.manifest(overlay.publicToken());

		assertThat(manifest.enabled()).isFalse();
		assertThat(manifest.layers()).isEmpty();
	}

	@Test
	void layerBoundsAreValidated() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));
		OverlayLayer layer = service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, 1.0, "Text", null, null));

		assertThatThrownBy(() -> service.updateLayer(overlay.id(), layer.id(), new UpdateOverlayLayerRequest(null, 1900, null, 300, null, null, null, null, null, null, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exceed");
	}

	@Test
	void overlaysAreIsolated() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay first = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));
		Overlay second = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay B", "chat", true, null));

		service.createLayer(first.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, 1.0, "A", null, null));

		assertThat(service.manifest(first.publicToken()).layers()).hasSize(1);
		assertThat(service.manifest(second.publicToken()).layers()).isEmpty();
	}

	@Test
	void visibleFalseLayerStillAppearsInManifest() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));
		service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, false, 1.0, "Hidden", null, null));

		OverlayManifestResponse manifest = service.manifest(overlay.publicToken());
		assertThat(manifest.layers()).hasSize(1);
		assertThat(manifest.layers().get(0).visible()).isFalse();
	}

	@Test
	void opacityZeroLayerRendersInManifest() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));
		service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, 0.0, "Invisible", null, null));

		OverlayManifestResponse manifest = service.manifest(overlay.publicToken());
		assertThat(manifest.layers()).hasSize(1);
		assertThat(manifest.layers().get(0).opacity()).isEqualTo(0.0);
	}

	@Test
	void negativeZIndexIsAccepted() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));
		service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, -5, true, 1.0, "Back", null, null));

		OverlayManifestResponse manifest = service.manifest(overlay.publicToken());
		assertThat(manifest.layers()).hasSize(1);
		assertThat(manifest.layers().get(0).zIndex()).isEqualTo(-5);
	}

	@Test
	void zeroWidthLayerIsRejected() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));

		assertThatThrownBy(() -> service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 0, 80, 1, true, 1.0, "Bad", null, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void zeroHeightLayerIsRejected() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));

		assertThatThrownBy(() -> service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 0, 1, true, 1.0, "Bad", null, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void negativeOpacityIsRejected() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));

		assertThatThrownBy(() -> service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, -0.5, "Bad", null, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void opacityAboveOneIsRejected() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));

		assertThatThrownBy(() -> service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, 10, 300, 80, 1, true, 1.5, "Bad", null, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void negativeXOrYIsRejected() {
		OverlayProfile profile = service.createProfile(new CreateOverlayProfileRequest("Demo", null));
		Overlay overlay = service.createOverlay(profile.id(), new CreateOverlayRequest("Overlay A", "chat", true, null));

		assertThatThrownBy(() -> service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, -1, 10, 300, 80, 1, true, 1.0, "Bad", null, null)))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> service.createLayer(overlay.id(), new CreateOverlayLayerRequest(OverlayLayerType.TEXT, 10, -1, 300, 80, 1, true, 1.0, "Bad", null, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
