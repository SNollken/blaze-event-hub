const $ = (id) => document.getElementById(id);

const sensitiveKeys = new Set([
	"accessToken",
	"access_token",
	"refreshToken",
	"refresh_token",
	"clientSecret",
	"client_secret",
	"codeVerifier",
	"code_verifier"
]);

function setText(id, value, tone) {
	const node = $(id);
	node.textContent = value == null || value === "" ? "-" : String(value);
	node.classList.remove("ok", "warn", "bad");
	if (tone) {
		node.classList.add(tone);
	}
}

function yesNo(value) {
	return value ? "sim" : "nao";
}

function tone(value) {
	return value ? "ok" : "warn";
}

function scrub(value) {
	if (Array.isArray(value)) {
		return value.map(scrub);
	}
	if (value && typeof value === "object") {
		return Object.fromEntries(Object.entries(value).map(([key, item]) => [
			key,
			sensitiveKeys.has(key) ? "[redacted]" : scrub(item)
		]));
	}
	return value;
}

function log(title, payload, isError = false) {
	const now = new Date().toLocaleTimeString();
	const safePayload = typeof payload === "string" ? payload : JSON.stringify(scrub(payload), null, 2);
	const entry = `[${now}] ${isError ? "ERRO" : "OK"} - ${title}\n${safePayload}\n\n`;
	const logs = $("logs");
	logs.textContent = entry + (logs.textContent === "Aguardando primeira leitura..." ? "" : logs.textContent);
}

async function request(path, options = {}) {
	const response = await fetch(path, {
		headers: {
			"Accept": "application/json",
			...(options.body ? {"Content-Type": "application/json"} : {})
		},
		...options
	});
	const contentType = response.headers.get("content-type") || "";
	const body = contentType.includes("application/json") ? await response.json() : await response.text();
	if (!response.ok) {
		const message = body && body.message ? body.message : `HTTP ${response.status}`;
		const error = new Error(message);
		error.status = response.status;
		error.body = body;
		throw error;
	}
	return body;
}

async function loadHealth() {
	try {
		const health = await request("/api/health");
		setText("backendStatus", health.status === "ok" ? "online" : "degradado", health.status === "ok" ? "ok" : "warn");
		return health;
	}
	catch (error) {
		setText("backendStatus", "offline", "bad");
		throw error;
	}
}

async function loadStatus() {
	const status = await request("/api/status");
	setText("appStatus", status.appName || "NollenBlaze");
	setText("versionStatus", `versao ${status.version || "-"}`);
	setText("javaStatus", status.javaVersion || "-");
	setText("oauthConfigured", yesNo(status.blazeOAuthConfigured), tone(status.blazeOAuthConfigured));
	setText("tokenStatus", yesNo(status.tokenPresent), tone(status.tokenPresent));
	setText("refreshCredentialStatus", `refresh credential: ${yesNo(status.refreshCredentialPresent)}`);
	setText("apiConfigured", yesNo(status.blazeApiConfigured), tone(status.blazeApiConfigured));
	setText("eventsConfigured", yesNo(status.socketConfigured), tone(status.socketConfigured));
	setText("eventsRunning", `runner: ${yesNo(status.eventsRunning)}`);
	setText("sessionStatus", status.sessionIdPresent ? "presente" : "sem sessionId", tone(status.sessionIdPresent));
	setText("channelStatus", status.monitoredChannelConfigured ? "configurado" : "nao configurado", tone(status.monitoredChannelConfigured));
	setText("profilesCount", status.activeProfilesCount ?? "-");
	setText("overlaysCount", status.overlaysCount ?? "-");
	setText("uptimeStatus", `${status.uptimeSeconds ?? 0}s`);
	return status;
}

async function loadEventsStatus() {
	const events = await request("/api/blaze/events/status");
	setText("eventsRunning", `runner: ${yesNo(events.runnerRunning)} / client: ${yesNo(events.clientRunning)}`);
	setText("sessionStatus", events.sessionId || "sem sessionId", events.sessionId ? "ok" : "warn");
	return events;
}

async function loadOverlayDemo() {
	const profiles = await request("/api/overlay-profiles");
	if (!profiles.length) {
		disableManifestLink();
		return {profiles, overlays: []};
	}
	const overlays = await request(`/api/overlay-profiles/${encodeURIComponent(profiles[0].id)}/overlays`);
	const demo = overlays.find((overlay) => overlay.publicToken) || overlays[0];
	if (demo && demo.publicToken) {
		const href = `/api/public/overlays/${encodeURIComponent(demo.publicToken)}/manifest`;
		$("manifestLink").href = href;
		$("manifestEndpoint").href = href;
		$("manifestLink").classList.remove("disabled");
	}
	else {
		disableManifestLink();
	}
	return {profiles, overlays};
}

function disableManifestLink() {
	$("manifestLink").href = "#";
	$("manifestEndpoint").href = "#";
	$("manifestLink").classList.add("disabled");
}

function markStatusUnavailable() {
	setText("appStatus", "indisponivel", "warn");
	setText("versionStatus", "-");
	setText("javaStatus", "-");
	setText("oauthConfigured", "indisponivel", "warn");
	setText("tokenStatus", "indisponivel", "warn");
	setText("refreshCredentialStatus", "refresh credential: indisponivel");
	setText("apiConfigured", "indisponivel", "warn");
	setText("eventsConfigured", "indisponivel", "warn");
	setText("channelStatus", "indisponivel", "warn");
	setText("profilesCount", "indisponivel", "warn");
	setText("overlaysCount", "indisponivel", "warn");
	setText("uptimeStatus", "-");
}

function markEventsUnavailable() {
	setText("eventsRunning", "runner: indisponivel", "warn");
	setText("sessionStatus", "sem sessionId", "warn");
}

function markOverlayUnavailable() {
	setText("profilesCount", "indisponivel", "warn");
	setText("overlaysCount", "indisponivel", "warn");
	disableManifestLink();
}

async function safeLoad(title, loader, fallback) {
	try {
		return {ok: true, value: await loader()};
	}
	catch (error) {
		fallback(error);
		log(title, error.body || error.message, true);
		return {ok: false, error};
	}
}

async function loadAll() {
	const [health, status, events, overlays] = await Promise.all([
		safeLoad("Health indisponivel", loadHealth, () => setText("backendStatus", "offline", "bad")),
		safeLoad("Status indisponivel", loadStatus, markStatusUnavailable),
		safeLoad("Events indisponivel", loadEventsStatus, markEventsUnavailable),
		safeLoad("Overlays indisponiveis", loadOverlayDemo, markOverlayUnavailable)
	]);

	log("Status atualizado", {
		health: health.ok ? health.value : "indisponivel",
		status: status.ok ? status.value : "indisponivel",
		events: events.ok ? events.value : "indisponivel",
		overlayProfiles: overlays.ok ? overlays.value.profiles.length : "indisponivel",
		overlays: overlays.ok ? overlays.value.overlays.length : "indisponivel"
	});
}

async function startOAuth() {
	try {
		const response = await request("/api/blaze/oauth/start", {method: "POST"});
		if (response.authorizationUrl) {
			const help = $("oauthHelp");
			help.textContent = "OAuth iniciado. ";
			const link = document.createElement("a");
			link.href = response.authorizationUrl;
			link.target = "_blank";
			link.rel = "noreferrer";
			link.textContent = "Abrir autorizacao Blaze";
			help.appendChild(link);
			window.open(response.authorizationUrl, "_blank", "noopener,noreferrer");
		}
		log("OAuth start", response);
	}
	catch (error) {
		const message = error.status === 503
			? "OAuth nao configurado. Confira BLAZE_CLIENT_ID, BLAZE_CLIENT_SECRET e BLAZE_REDIRECT_URI no backend."
			: error.message;
		$("oauthHelp").textContent = message;
		log("OAuth start", error.body || message, true);
	}
}

async function postAction(title, path) {
	try {
		const response = await request(path, {method: "POST"});
		log(title, response);
		await loadAll();
	}
	catch (error) {
		const message = error.status === 503
			? `${title}: configuracao incompleta para esta acao. Verifique credenciais, sessionId e canal monitorado.`
			: error.message;
		log(title, error.body || message, true);
	}
}

$("refreshStatus").addEventListener("click", loadAll);
$("startOAuth").addEventListener("click", startOAuth);
$("refreshEvents").addEventListener("click", async () => {
	try {
		log("Events status", await loadEventsStatus());
	}
	catch (error) {
		log("Events status", error.body || error.message, true);
	}
});
$("startEvents").addEventListener("click", () => postAction("Start Events", "/api/blaze/events/start"));
$("stopEvents").addEventListener("click", () => postAction("Stop Events", "/api/blaze/events/stop"));
$("syncSubscriptions").addEventListener("click", () => postAction("Sync subscriptions", "/api/blaze/events/subscriptions/sync"));

loadAll();
