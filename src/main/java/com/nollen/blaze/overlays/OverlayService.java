package com.nollen.blaze.overlays;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OverlayService {

	private static final long MAX_ASSET_BYTES = 10 * 1024 * 1024;
	private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/png", "image/gif", "image/webp");
	private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "html", "js", "svg");
	static final String DEMO_PUBLIC_TOKEN = "demo-overlay-obs-mvp";

	private final OverlayRepository repository;
	private final OverlayAssetStorage assetStorage;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public OverlayService(OverlayRepository repository, OverlayAssetStorage assetStorage, IdGenerator idGenerator, Clock clock) {
		this.repository = repository;
		this.assetStorage = assetStorage;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	@PostConstruct
	void seedDevData() {
		if (repository.countProfiles() > 0) {
			return;
		}
		OverlayProfile profile = createProfile(new CreateOverlayProfileRequest("Demo", "Perfil de demonstracao local"));
		Instant now = Instant.now(clock);
		Overlay overlay = repository.saveOverlay(new Overlay(
				idGenerator.newId(),
				profile.id(),
				"Overlay de Teste",
				"demo",
				DEMO_PUBLIC_TOKEN,
				true,
				OverlayConfig.defaultConfig(),
				List.of(),
				List.of(),
				now,
				now));
		createLayer(overlay.id(), new CreateOverlayLayerRequest(
				OverlayLayerType.TEXT,
				80,
				80,
				760,
				120,
				1,
				true,
				1.0,
				"NollenBlaze Overlay Demo",
				null,
				Map.of("fontSize", 56, "fontWeight", "700", "color", "#ffffff", "textAlign", "left")));
		createLayer(overlay.id(), new CreateOverlayLayerRequest(
				OverlayLayerType.SHAPE,
				80,
				800,
				400,
				8,
				0,
				true,
				0.6,
				null,
				null,
				Map.of("backgroundColor", "#00d4ff", "borderRadius", "4")));
	}

	public List<OverlayProfile> listProfiles() {
		return repository.listProfiles();
	}

	public OverlayProfile getProfile(String profileId) {
		return repository.findProfile(profileId)
				.orElseThrow(() -> new NotFoundException("Overlay profile not found"));
	}

	public OverlayProfile createProfile(CreateOverlayProfileRequest request) {
		Instant now = Instant.now(clock);
		return repository.saveProfile(new OverlayProfile(
				idGenerator.newId(),
				request.name().trim(),
				clean(request.description()),
				now,
				now));
	}

	public OverlayProfile updateProfile(String profileId, UpdateOverlayProfileRequest request) {
		OverlayProfile current = getProfile(profileId);
		OverlayProfile updated = new OverlayProfile(
				current.id(),
				request.name().trim(),
				clean(request.description()),
				current.createdAt(),
				Instant.now(clock));
		return repository.saveProfile(updated);
	}

	public void deleteProfile(String profileId) {
		getProfile(profileId);
		repository.deleteProfile(profileId);
	}

	public List<Overlay> listOverlays(String profileId) {
		getProfile(profileId);
		return repository.listOverlays(profileId);
	}

	public Overlay getOverlay(String overlayId) {
		return repository.findOverlay(overlayId)
				.orElseThrow(() -> new NotFoundException("Overlay not found"));
	}

	public Overlay createOverlay(String profileId, CreateOverlayRequest request) {
		getProfile(profileId);
		Instant now = Instant.now(clock);
		Overlay overlay = new Overlay(
				idGenerator.newId(),
				profileId,
				request.name().trim(),
				request.type().trim(),
				idGenerator.newPublicToken(),
				request.enabled() == null || request.enabled(),
				request.config() == null ? OverlayConfig.defaultConfig() : request.config().normalized(),
				List.of(),
				List.of(),
				now,
				now);
		return repository.saveOverlay(overlay);
	}

	public Overlay updateOverlay(String overlayId, UpdateOverlayRequest request) {
		Overlay current = getOverlay(overlayId);
		Overlay updated = new Overlay(
				current.id(),
				current.profileId(),
				request.name() == null || request.name().isBlank() ? current.name() : request.name().trim(),
				request.type() == null || request.type().isBlank() ? current.type() : request.type().trim(),
				current.publicToken(),
				request.enabled() == null ? current.enabled() : request.enabled(),
				request.config() == null ? current.config() : request.config().normalized(),
				current.layers(),
				current.assets(),
				current.createdAt(),
				Instant.now(clock));
		return repository.saveOverlay(updated);
	}

	public void deleteOverlay(String overlayId) {
		Overlay overlay = getOverlay(overlayId);
		overlay.assets().forEach(asset -> assetStorage.delete(asset.id()));
		repository.deleteOverlay(overlay.id());
	}

	public List<OverlayLayer> listLayers(String overlayId) {
		return getOverlay(overlayId).layers();
	}

	public OverlayLayer createLayer(String overlayId, CreateOverlayLayerRequest request) {
		Overlay overlay = getOverlay(overlayId);
		validateLayerBounds(request.x(), request.y(), request.width(), request.height(), overlay.config());
		OverlayLayer layer = new OverlayLayer(
				idGenerator.newId(),
				overlay.id(),
				request.type(),
				request.x(),
				request.y(),
				request.width(),
				request.height(),
				request.zIndex(),
				request.visible() == null || request.visible(),
				request.opacity() == null ? 1.0 : clampOpacity(request.opacity()),
				clean(request.text()),
				clean(request.assetId()),
				request.style());
		List<OverlayLayer> layers = new ArrayList<>(overlay.layers());
		layers.add(layer);
		repository.saveOverlay(copyWithLayers(overlay, layers));
		return layer;
	}

	public OverlayLayer updateLayer(String overlayId, String layerId, UpdateOverlayLayerRequest request) {
		Overlay overlay = getOverlay(overlayId);
		OverlayLayer current = overlay.layers().stream()
				.filter(layer -> layer.id().equals(layerId))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Overlay layer not found"));
		int x = request.x() == null ? current.x() : request.x();
		int y = request.y() == null ? current.y() : request.y();
		int width = request.width() == null ? current.width() : request.width();
		int height = request.height() == null ? current.height() : request.height();
		validateLayerBounds(x, y, width, height, overlay.config());
		OverlayLayer updated = new OverlayLayer(
				current.id(),
				overlay.id(),
				request.type() == null ? current.type() : request.type(),
				x,
				y,
				width,
				height,
				request.zIndex() == null ? current.zIndex() : request.zIndex(),
				request.visible() == null ? current.visible() : request.visible(),
				request.opacity() == null ? current.opacity() : clampOpacity(request.opacity()),
				request.text() == null ? current.text() : clean(request.text()),
				request.assetId() == null ? current.assetId() : clean(request.assetId()),
				request.style() == null ? current.style() : request.style());
		List<OverlayLayer> layers = overlay.layers().stream()
				.map(layer -> layer.id().equals(layerId) ? updated : layer)
				.toList();
		repository.saveOverlay(copyWithLayers(overlay, layers));
		return updated;
	}

	public void deleteLayer(String overlayId, String layerId) {
		Overlay overlay = getOverlay(overlayId);
		boolean exists = overlay.layers().stream().anyMatch(layer -> layer.id().equals(layerId));
		if (!exists) {
			throw new NotFoundException("Overlay layer not found");
		}
		repository.saveOverlay(copyWithLayers(overlay, overlay.layers().stream()
				.filter(layer -> !layer.id().equals(layerId))
				.toList()));
	}

	public OverlayManifestResponse manifest(String publicToken) {
		if (!StringUtils.hasText(publicToken)) {
			throw new NotFoundException("Overlay manifest not found");
		}
		Overlay overlay = repository.findByPublicToken(publicToken)
				.orElseThrow(() -> new NotFoundException("Overlay manifest not found"));
		return OverlayManifestResponse.from(overlay);
	}

	public OverlayAsset addAsset(String overlayId, MultipartFile file) throws IOException {
		Overlay overlay = getOverlay(overlayId);
		validateAsset(file);
		byte[] bytes = file.getBytes();
		String originalFilename = safeFilename(file.getOriginalFilename());
		String assetId = idGenerator.newId();
		String storedFilename = assetId + "-" + originalFilename;
		OverlayAsset asset = new OverlayAsset(
				assetId,
				overlay.id(),
				originalFilename,
				storedFilename,
				file.getContentType(),
				file.getSize(),
				null,
				null,
				sha256(bytes),
				Instant.now(clock));
		assetStorage.store(asset.id(), bytes);
		List<OverlayAsset> assets = new ArrayList<>(overlay.assets());
		assets.add(asset);
		repository.saveOverlay(copyWithAssets(overlay, assets));
		return asset;
	}

	private Overlay copyWithLayers(Overlay overlay, List<OverlayLayer> layers) {
		return new Overlay(overlay.id(), overlay.profileId(), overlay.name(), overlay.type(), overlay.publicToken(),
				overlay.enabled(), overlay.config(), layers, overlay.assets(), overlay.createdAt(), Instant.now(clock));
	}

	private Overlay copyWithAssets(Overlay overlay, List<OverlayAsset> assets) {
		return new Overlay(overlay.id(), overlay.profileId(), overlay.name(), overlay.type(), overlay.publicToken(),
				overlay.enabled(), overlay.config(), overlay.layers(), assets, overlay.createdAt(), Instant.now(clock));
	}

	private static void validateLayerBounds(int x, int y, int width, int height, OverlayConfig config) {
		if (x < 0 || y < 0 || width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Layer bounds must be positive");
		}
		if (x + width > config.canvasWidth() || y + height > config.canvasHeight()) {
			throw new IllegalArgumentException("Layer bounds exceed overlay canvas");
		}
	}

	private static double clampOpacity(double opacity) {
		if (opacity < 0.0 || opacity > 1.0) {
			throw new IllegalArgumentException("Layer opacity must be between 0 and 1");
		}
		return opacity;
	}

	private static void validateAsset(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Asset file is required");
		}
		if (file.getSize() > MAX_ASSET_BYTES) {
			throw new IllegalArgumentException("Asset file is too large");
		}
		String filename = safeFilename(file.getOriginalFilename());
		String extension = extension(filename);
		if (BLOCKED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("Asset file type is blocked");
		}
		if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
			throw new IllegalArgumentException("Asset mime type is not allowed");
		}
	}

	private static String safeFilename(String originalFilename) {
		String filename = StringUtils.getFilename(originalFilename == null ? "asset" : originalFilename);
		if (filename == null || filename.isBlank() || filename.contains("..")) {
			throw new IllegalArgumentException("Invalid asset filename");
		}
		return filename;
	}

	private static String extension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}
}
