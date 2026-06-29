(function () {
	"use strict";

	var root = document.getElementById("overlayRoot");
	var stage = document.getElementById("overlayStage");
	var message = document.getElementById("overlayMessage");
	var obsBrowser = /obs|browser source/i.test(window.navigator.userAgent || "");

	var params = new URLSearchParams(window.location.search);
	var debugMode = params.get("debug") === "1" || params.get("debug") === "true";
	var fitMode = params.get("fit") || "contain";

	function readPublicToken() {
		var parts = window.location.pathname.split("/").filter(Boolean);
		var overlayIndex = parts.indexOf("overlay");
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
		var template = root.dataset.manifestTemplate || "/api/public/overlays/{publicToken}/manifest";
		return template.replace("{publicToken}", encodeURIComponent(publicToken));
	}

	function showMessage(text, isError) {
		stage.textContent = "";
		if (obsBrowser && !debugMode) {
			message.hidden = true;
			message.textContent = "";
			return;
		}
		message.textContent = text;
		message.hidden = false;
		if (isError) {
			message.classList.add("overlay-message--error");
		} else {
			message.classList.remove("overlay-message--error");
		}
	}

	function hideMessage() {
		message.textContent = "";
		message.hidden = true;
		message.classList.remove("overlay-message--error");
	}

	function applyDebugMode() {
		if (!debugMode) {
			return;
		}
		root.classList.add("debug");
		var debugEl = document.createElement("div");
		debugEl.className = "debug-info";
		debugEl.id = "debugInfo";
		root.appendChild(debugEl);
	}

	function updateDebugInfo(data) {
		var el = document.getElementById("debugInfo");
		if (!el) {
			return;
		}
		var token = readPublicToken();
		var masked = token.length > 8 ? token.substring(0, 4) + "..." + token.substring(token.length - 4) : token;
		var lines = [
			"token: " + masked,
			"layers: " + (data.layerCount != null ? data.layerCount : "?"),
			"canvas: " + (data.canvasWidth || "?") + "x" + (data.canvasHeight || "?"),
			"status: " + (data.status || "ok")
		];
		el.textContent = lines.join("\n");
	}

	async function loadManifest(publicToken) {
		var response = await fetch(manifestUrl(publicToken), {
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
		var config = normalizedConfig(manifest.config);
		applyStageConfig(config);
		stage.textContent = "";

		if (manifest.enabled === false) {
			showMessage("Overlay desativada.");
			if (debugMode) {
				updateDebugInfo({status: "disabled", canvasWidth: config.canvasWidth, canvasHeight: config.canvasHeight, layerCount: 0});
			}
			return;
		}

		var layers = Array.isArray(manifest.layers)
			? manifest.layers.filter(function (layer) { return layer && layer.visible !== false; })
			: [];

		if (!layers.length) {
			showMessage("Overlay sem camadas.");
			if (debugMode) {
				updateDebugInfo({status: "no-layers", canvasWidth: config.canvasWidth, canvasHeight: config.canvasHeight, layerCount: 0});
			}
			return;
		}

		var assets = Array.isArray(manifest.assets) ? manifest.assets : [];
		hideMessage();

		var sorted = layers.slice().sort(function (left, right) {
			return number(left.zIndex, 0) - number(right.zIndex, 0);
		});

		for (var i = 0; i < sorted.length; i++) {
			try {
				var node = renderLayer(sorted[i], config, assets);
				if (node) {
					stage.appendChild(node);
				}
			}
			catch (layerError) {
				// skip broken layer silently
			}
		}

		if (debugMode) {
			updateDebugInfo({status: "ok", canvasWidth: config.canvasWidth, canvasHeight: config.canvasHeight, layerCount: layers.length});
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
		stage.style.aspectRatio = config.canvasWidth + " / " + config.canvasHeight;
		stage.style.maxWidth = "calc(100vh * " + config.canvasWidth + " / " + config.canvasHeight + ")";
		stage.style.maxHeight = "calc(100vw * " + config.canvasHeight + " / " + config.canvasWidth + ")";
		stage.style.setProperty("--overlay-font-family", config.defaultFontFamily);
		stage.style.setProperty("--overlay-text-color", config.defaultTextColor);
		stage.classList.toggle("has-background", !config.transparent);
		stage.style.setProperty("--overlay-background", config.transparent ? "transparent" : config.backgroundColor);
	}

	function renderLayer(layer, config, assets) {
		var type = text(layer.type, "TEXT").toUpperCase();
		if (type === "IMAGE") {
			return renderImageLayer(layer, config, assets);
		}
		if (type === "SHAPE") {
			return renderShapeLayer(layer, config);
		}
		return renderTextLayer(layer, config);
	}

	function renderTextLayer(layer, config) {
		var node = baseLayer(layer, config, "overlay-layer--text");
		var style = styleObject(layer.style);
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
		var src = imageSource(layer, assets);
		if (!src) {
			return null;
		}
		var node = baseLayer(layer, config, "overlay-layer--image");
		var img = document.createElement("img");
		img.alt = "";
		img.decoding = "async";
		img.src = src;
		img.onerror = function () {
			img.style.display = "none";
		};
		var style = styleObject(layer.style);
		img.style.objectFit = objectFit(style.objectFit);
		node.appendChild(img);
		return node;
	}

	function renderShapeLayer(layer, config) {
		var node = baseLayer(layer, config, "overlay-layer--shape");
		var style = styleObject(layer.style);
		node.style.background = cssColor(style.backgroundColor || style.color, "rgba(255, 255, 255, 0.2)");
		node.style.borderRadius = length(style.borderRadius, "0px");
		return node;
	}

	function baseLayer(layer, config, className) {
		var node = document.createElement("div");
		node.className = "overlay-layer " + className;
		var left = percent(number(layer.x, 0), config.canvasWidth);
		var top = percent(number(layer.y, 0), config.canvasHeight);
		var width = percent(positiveNumber(layer.width, 1), config.canvasWidth);
		var height = percent(positiveNumber(layer.height, 1), config.canvasHeight);
		node.style.left = left + "%";
		node.style.top = top + "%";
		node.style.width = width + "%";
		node.style.height = height + "%";
		var zi = number(layer.zIndex, 0);
		node.style.zIndex = String(Number.isFinite(zi) ? Math.trunc(zi) : 0);
		var op = number(layer.opacity, 1);
		node.style.opacity = String(Number.isFinite(op) ? clamp(op, 0, 1) : 1);
		return node;
	}

	function imageSource(layer, assets) {
		var style = styleObject(layer.style);
		var direct = safeUrl(style.src || style.url || style.imageUrl || style.publicUrl);
		if (direct) {
			return direct;
		}
		var assetId = text(layer.assetId, "");
		var asset = null;
		for (var i = 0; i < assets.length; i++) {
			if (assets[i] && assets[i].id === assetId) {
				asset = assets[i];
				break;
			}
		}
		if (!asset) {
			return "";
		}
		return safeUrl(asset.publicUrl || asset.url || asset.src || asset.href || "");
	}

	function safeUrl(value) {
		var raw = text(value, "");
		if (!raw) {
			return "";
		}
		try {
			var url = new URL(raw, window.location.origin);
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
		var parsed = Number(value);
		return Number.isFinite(parsed) ? parsed : fallback;
	}

	function positiveNumber(value, fallback) {
		var parsed = number(value, fallback);
		return parsed > 0 ? parsed : fallback;
	}

	function percent(value, total) {
		return clamp((value / total) * 100, 0, 100);
	}

	function clamp(value, min, max) {
		return Math.max(min, Math.min(max, value));
	}

	function cssColor(value, fallback) {
		var raw = text(value, "");
		if (/^#[0-9a-f]{3,8}$/i.test(raw) || /^rgba?\([0-9\s.,%]+\)$/i.test(raw)) {
			return raw;
		}
		return fallback;
	}

	function fontSize(value, fallback) {
		if (typeof value === "number" && Number.isFinite(value) && value > 0) {
			return Math.min(value, 240) + "px";
		}
		var raw = text(value, "");
		if (/^[0-9]+(\.[0-9]+)?px$/i.test(raw)) {
			return raw;
		}
		return fallback + "px";
	}

	function length(value, fallback) {
		if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
			return Math.min(value, 9999) + "px";
		}
		var raw = text(value, "");
		if (/^[0-9]+(\.[0-9]+)?(px|%)$/i.test(raw)) {
			return raw;
		}
		return fallback;
	}

	function fontWeight(value) {
		var raw = text(String(value || ""), "");
		if (/^[1-9]00$/.test(raw) || ["normal", "bold", "bolder", "lighter"].indexOf(raw) >= 0) {
			return raw;
		}
		return "700";
	}

	function textAlign(value) {
		var raw = text(value, "left").toLowerCase();
		return ["left", "center", "right"].indexOf(raw) >= 0 ? raw : "left";
	}

	function justifyContent(align) {
		return align === "center" ? "center" : align === "right" ? "flex-end" : "flex-start";
	}

	function alignItems(value) {
		var raw = text(value, "center").toLowerCase();
		if (raw === "top") {
			return "flex-start";
		}
		if (raw === "bottom") {
			return "flex-end";
		}
		return "center";
	}

	function objectFit(value) {
		var raw = text(value, "contain").toLowerCase();
		return ["contain", "cover", "fill", "scale-down"].indexOf(raw) >= 0 ? raw : "contain";
	}

	async function boot() {
		applyDebugMode();
		var publicToken = readPublicToken();
		if (!publicToken) {
			showMessage("Overlay nao encontrada.", true);
			if (debugMode) {
				updateDebugInfo({status: "no-token"});
			}
			return;
		}
		try {
			renderManifest(await loadManifest(publicToken));
		}
		catch (error) {
			if (error.kind === "not-found") {
				showMessage("Overlay nao encontrada.", true);
				if (debugMode) {
					updateDebugInfo({status: "404"});
				}
				return;
			}
			if (error.kind === "invalid") {
				showMessage("Manifest invalido.", true);
				if (debugMode) {
					updateDebugInfo({status: "invalid-manifest"});
				}
				return;
			}
			showMessage("Erro ao carregar overlay.", true);
			if (debugMode) {
				updateDebugInfo({status: "error"});
			}
		}
	}

	boot();
})();
