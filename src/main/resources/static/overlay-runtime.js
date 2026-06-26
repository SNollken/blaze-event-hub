(function () {
	const root = document.getElementById("overlayRoot");
	const stage = document.getElementById("overlayStage");
	const message = document.getElementById("overlayMessage");
	const obsBrowser = /obs|browser source/i.test(window.navigator.userAgent || "");

	function readPublicToken() {
		const parts = window.location.pathname.split("/").filter(Boolean);
		const overlayIndex = parts.indexOf("overlay");
		if (overlayIndex < 0 || overlayIndex + 1 >= parts.length) {
			return "";
		}
		try {
			return decodeURIComponent(parts[overlayIndex + 1]).trim();
		}
		catch (error) {
			return "";
		}
	}

	function manifestUrl(publicToken) {
		const template = root.dataset.manifestTemplate || "/api/public/overlays/{publicToken}/manifest";
		return template.replace("{publicToken}", encodeURIComponent(publicToken));
	}

	function showMessage(text) {
		stage.textContent = "";
		if (obsBrowser) {
			message.hidden = true;
			message.textContent = "";
			return;
		}
		message.textContent = text;
		message.hidden = false;
	}

	function hideMessage() {
		message.textContent = "";
		message.hidden = true;
	}

	async function loadManifest(publicToken) {
		const response = await fetch(manifestUrl(publicToken), {
			headers: {"Accept": "application/json"},
			cache: "no-store"
		});
		if (response.status === 404) {
			throw new RuntimeError("not-found");
		}
		if (!response.ok) {
			throw new RuntimeError("network");
		}
		try {
			return await response.json();
		}
		catch (error) {
			throw new RuntimeError("invalid");
		}
	}

	function RuntimeError(kind) {
		this.kind = kind;
	}

	function renderManifest(manifest) {
		if (!manifest || typeof manifest !== "object" || !manifest.config) {
			throw new RuntimeError("invalid");
		}
		const config = normalizedConfig(manifest.config);
		applyStageConfig(config);
		stage.textContent = "";

		if (manifest.enabled === false) {
			showMessage("Overlay desativada.");
			return;
		}

		const layers = Array.isArray(manifest.layers)
			? manifest.layers.filter((layer) => layer && layer.visible !== false)
			: [];
		if (!layers.length) {
			showMessage("Overlay sem camadas.");
			return;
		}

		const assets = Array.isArray(manifest.assets) ? manifest.assets : [];
		hideMessage();
		for (const layer of [...layers].sort((left, right) => number(left.zIndex, 0) - number(right.zIndex, 0))) {
			const node = renderLayer(layer, config, assets);
			if (node) {
				stage.appendChild(node);
			}
		}
	}

	function normalizedConfig(config) {
		return {
			canvasWidth: positiveNumber(config.canvasWidth, 1920),
			canvasHeight: positiveNumber(config.canvasHeight, 1080),
			transparent: config.transparent !== false,
			backgroundColor: cssColor(config.backgroundColor, "#000000"),
			defaultFontFamily: text(config.defaultFontFamily, "Inter, Arial, sans-serif"),
			defaultTextColor: cssColor(config.defaultTextColor, "#ffffff")
		};
	}

	function applyStageConfig(config) {
		stage.style.aspectRatio = `${config.canvasWidth} / ${config.canvasHeight}`;
		stage.style.maxWidth = `calc(100vh * ${config.canvasWidth} / ${config.canvasHeight})`;
		stage.style.maxHeight = `calc(100vw * ${config.canvasHeight} / ${config.canvasWidth})`;
		stage.style.setProperty("--overlay-font-family", config.defaultFontFamily);
		stage.style.setProperty("--overlay-text-color", config.defaultTextColor);
		stage.classList.toggle("has-background", !config.transparent);
		stage.style.setProperty("--overlay-background", config.transparent ? "transparent" : config.backgroundColor);
	}

	function renderLayer(layer, config, assets) {
		const type = text(layer.type, "TEXT").toUpperCase();
		if (type === "IMAGE") {
			return renderImageLayer(layer, config, assets);
		}
		if (type === "SHAPE") {
			return renderShapeLayer(layer, config);
		}
		return renderTextLayer(layer, config);
	}

	function renderTextLayer(layer, config) {
		const node = baseLayer(layer, config, "overlay-layer--text");
		const style = styleObject(layer.style);
		node.textContent = text(layer.text, "");
		node.style.color = cssColor(style.color, config.defaultTextColor);
		node.style.fontFamily = text(style.fontFamily, config.defaultFontFamily);
		node.style.fontSize = fontSize(style.fontSize, 42);
		node.style.fontWeight = fontWeight(style.fontWeight);
		node.style.textAlign = textAlign(style.textAlign || style.align);
		node.style.justifyContent = justifyContent(node.style.textAlign);
		node.style.alignItems = alignItems(style.verticalAlign);
		return node;
	}

	function renderImageLayer(layer, config, assets) {
		const src = imageSource(layer, assets);
		if (!src) {
			return null;
		}
		const node = baseLayer(layer, config, "overlay-layer--image");
		const img = document.createElement("img");
		img.alt = "";
		img.decoding = "async";
		img.src = src;
		img.style.objectFit = objectFit(styleObject(layer.style).objectFit);
		node.appendChild(img);
		return node;
	}

	function renderShapeLayer(layer, config) {
		const node = baseLayer(layer, config, "overlay-layer--shape");
		const style = styleObject(layer.style);
		node.style.background = cssColor(style.backgroundColor || style.color, "rgba(255, 255, 255, 0.2)");
		node.style.borderRadius = length(style.borderRadius, "0px");
		return node;
	}

	function baseLayer(layer, config, className) {
		const node = document.createElement("div");
		node.className = `overlay-layer ${className}`;
		const left = percent(number(layer.x, 0), config.canvasWidth);
		const top = percent(number(layer.y, 0), config.canvasHeight);
		const width = percent(positiveNumber(layer.width, 1), config.canvasWidth);
		const height = percent(positiveNumber(layer.height, 1), config.canvasHeight);
		node.style.left = `${left}%`;
		node.style.top = `${top}%`;
		node.style.width = `${width}%`;
		node.style.height = `${height}%`;
		node.style.zIndex = String(Math.trunc(number(layer.zIndex, 0)));
		node.style.opacity = String(clamp(number(layer.opacity, 1), 0, 1));
		return node;
	}

	function imageSource(layer, assets) {
		const style = styleObject(layer.style);
		const direct = safeUrl(style.src || style.url || style.imageUrl || style.publicUrl);
		if (direct) {
			return direct;
		}
		const assetId = text(layer.assetId, "");
		const asset = assets.find((item) => item && item.id === assetId);
		if (!asset) {
			return "";
		}
		return safeUrl(asset.url || asset.publicUrl || asset.src || asset.href || "");
	}

	function safeUrl(value) {
		const raw = text(value, "");
		if (!raw) {
			return "";
		}
		try {
			const url = new URL(raw, window.location.origin);
			if (url.protocol === "http:" || url.protocol === "https:") {
				return url.href;
			}
			if (url.protocol === "data:" && /^data:image\/(png|gif|webp|jpeg);base64,/i.test(raw)) {
				return raw;
			}
		}
		catch (error) {
			return "";
		}
		return "";
	}

	function styleObject(value) {
		return value && typeof value === "object" && !Array.isArray(value) ? value : {};
	}

	function text(value, fallback) {
		return typeof value === "string" && value.trim() !== "" ? value.trim() : fallback;
	}

	function number(value, fallback) {
		const parsed = Number(value);
		return Number.isFinite(parsed) ? parsed : fallback;
	}

	function positiveNumber(value, fallback) {
		const parsed = number(value, fallback);
		return parsed > 0 ? parsed : fallback;
	}

	function percent(value, total) {
		return clamp((value / total) * 100, 0, 100);
	}

	function clamp(value, min, max) {
		return Math.max(min, Math.min(max, value));
	}

	function cssColor(value, fallback) {
		const raw = text(value, "");
		if (/^#[0-9a-f]{3,8}$/i.test(raw) || /^rgba?\([0-9\s.,%]+\)$/i.test(raw)) {
			return raw;
		}
		return fallback;
	}

	function fontSize(value, fallback) {
		if (typeof value === "number" && Number.isFinite(value) && value > 0) {
			return `${Math.min(value, 240)}px`;
		}
		const raw = text(value, "");
		if (/^[0-9]+(\.[0-9]+)?px$/i.test(raw)) {
			return raw;
		}
		return `${fallback}px`;
	}

	function length(value, fallback) {
		if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
			return `${Math.min(value, 9999)}px`;
		}
		const raw = text(value, "");
		if (/^[0-9]+(\.[0-9]+)?(px|%)$/i.test(raw)) {
			return raw;
		}
		return fallback;
	}

	function fontWeight(value) {
		const raw = text(String(value || ""), "");
		if (/^[1-9]00$/.test(raw) || ["normal", "bold", "bolder", "lighter"].includes(raw)) {
			return raw;
		}
		return "700";
	}

	function textAlign(value) {
		const raw = text(value, "left").toLowerCase();
		return ["left", "center", "right"].includes(raw) ? raw : "left";
	}

	function justifyContent(align) {
		return align === "center" ? "center" : align === "right" ? "flex-end" : "flex-start";
	}

	function alignItems(value) {
		const raw = text(value, "center").toLowerCase();
		if (raw === "top") {
			return "flex-start";
		}
		if (raw === "bottom") {
			return "flex-end";
		}
		return "center";
	}

	function objectFit(value) {
		const raw = text(value, "contain").toLowerCase();
		return ["contain", "cover", "fill", "scale-down"].includes(raw) ? raw : "contain";
	}

	async function boot() {
		const publicToken = readPublicToken();
		if (!publicToken) {
			showMessage("Overlay nao encontrada.");
			return;
		}
		try {
			renderManifest(await loadManifest(publicToken));
		}
		catch (error) {
			if (error.kind === "not-found") {
				showMessage("Overlay nao encontrada.");
				return;
			}
			if (error.kind === "invalid") {
				showMessage("Manifest invalido.");
				return;
			}
			showMessage("Erro ao carregar overlay.");
		}
	}

	boot();
})();
