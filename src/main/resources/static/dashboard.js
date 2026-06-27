const $ = (id) => document.getElementById(id);

const sensitiveKeys = new Set([
	"accesstoken",
	"refreshtoken",
	"clientsecret",
	"codeverifier",
	"code",
	"state",
	"authorizationurl",
	"authorization"
]);

let currentSetup = null;
let currentOAuthSession = null;
let disconnectConfirmationPending = false;
let disconnectConfirmationAt = 0;

function setText(id, value, tone) {
	const node = $(id);
	if (!node) {
		return;
	}
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
			isSensitiveKey(key) ? "[redacted]" : key,
			isSensitiveKey(key) ? "[redacted]" : scrub(item)
		]));
	}
	if (typeof value === "string") {
		return scrubString(value);
	}
	return value;
}

function isSensitiveKey(key) {
	const normalized = String(key).replace(/[_-]/g, "").toLowerCase();
	return sensitiveKeys.has(normalized);
}

function scrubString(value) {
	return value
		.replace(/bearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer [redacted]")
		.replace(/https?:\/\/[^\s"']*oauth[^\s"']*/gi, "[redacted-url]")
		.replace(/\b(clientSecret|client_secret|accessToken|access_token|refreshToken|refresh_token|codeVerifier|code_verifier|authorizationUrl|authorization_url|code|state)\s*[=:]\s*[^\s,}&]+/gi, "[redacted]");
}

function log(title, payload, isError = false) {
	const now = new Date().toLocaleTimeString();
	const safePayload = typeof payload === "string" ? scrubString(payload) : JSON.stringify(scrub(payload), null, 2);
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
	setText("oauthAccountStatus", status.oauthConnected ? "conectada" : "desconectada", tone(status.oauthConnected));
	setText("oauthProfileStatus", status.profilePresent ? "perfil sincronizado" : "perfil ausente", tone(status.profilePresent));
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

async function loadOAuthSession() {
	const session = await request("/api/blaze/oauth/session");
	renderOAuthSession(session);
	return session;
}

function renderOAuthSession(session) {
	currentOAuthSession = session;
	disconnectConfirmationPending = false;
	const connected = Boolean(session.connected);
	const profile = session.profile || {};
	const display = profile.displayName || profile.username || session.userId || "Conta Blaze";
	const accountId = profile.id || session.userId;

	setText("oauthAccountStatus", connected ? "conectada" : "desconectada", connected ? "ok" : "warn");
	setText("oauthProfileStatus", session.profilePresent ? "perfil sincronizado" : "perfil ausente", session.profilePresent ? "ok" : "warn");
	setText("oauthConnectedLabel", connected ? "Blaze conectada" : "Blaze nao conectada", connected ? "ok" : "warn");
	setText("oauthAccountName", connected ? `Conectado como: ${display}` : "Use Conectar Blaze para iniciar OAuth.");
	setText("oauthAccountId", accountId ? `id: ${maskIdentifier(accountId)}` : "id nao sincronizado");
	setText("oauthTokenLabel", session.tokenPresent ? "token: presente" : "token: ausente", tone(session.tokenPresent));
	setText("oauthRefreshLabel", `renovacao: ${yesNo(session.refreshCredentialPresent)}`);
	setText("oauthExpiresAt", session.expiresAt ? `expira: ${formatDate(session.expiresAt)}` : "expiracao desconhecida");
	setText("oauthProfileLabel", session.profilePresent ? "sincronizado" : "nao sincronizado", tone(session.profilePresent));
	setText("oauthLastProfileSync", session.lastProfileSyncAt ? `ultima sync: ${formatDate(session.lastProfileSyncAt)}` : "sem sync de perfil");
	setText("oauthNextAction", `proxima acao: ${translateNextAction(session.nextRecommendedAction)}`);
	setText("oauthAccountHelp", accountHelp(session));
	renderAvatar(profile, display);
	updateOAuthActionButtons(session);
}

function accountHelp(session) {
	if (!session.connected) {
		return "Conecte a Blaze para liberar perfil seguro e preparar Events real.";
	}
	if (!session.profilePresent) {
		return "Sessao conectada. Use Atualizar sessao para tentar sincronizar o perfil.";
	}
	if (!session.refreshCredentialPresent) {
		return "Conta conectada, mas sem credencial de renovacao. Refaca OAuth com offline.access quando necessario.";
	}
	return "Conta conectada e pronta para o proximo passo de Events real.";
}

function translateNextAction(action) {
	const labels = {
		CONNECT_BLAZE: "conectar Blaze",
		REFRESH_SESSION: "atualizar sessao",
		SYNC_PROFILE_OR_REFRESH_SESSION: "sincronizar perfil",
		RECONNECT_WITH_OFFLINE_ACCESS: "reconectar com offline.access",
		READY_FOR_EVENTS: "pronta para Events"
	};
	return labels[action] || "verificar setup";
}

function formatDate(value) {
	try {
		return new Date(value).toLocaleString();
	}
	catch (error) {
		return "-";
	}
}

function maskIdentifier(value) {
	const text = String(value);
	if (text.length <= 10) {
		return text;
	}
	return `${text.slice(0, 4)}...${text.slice(-4)}`;
}

function initials(display) {
	const text = String(display || "NB").trim();
	if (!text) {
		return "NB";
	}
	return text.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}

function renderAvatar(profile, display) {
	const avatar = $("oauthAvatar");
	if (!avatar) {
		return;
	}
	if (profile && profile.avatarUrl) {
		avatar.style.backgroundImage = `url("${profile.avatarUrl}")`;
		avatar.textContent = "";
	}
	else {
		avatar.style.backgroundImage = "";
		avatar.textContent = initials(display);
	}
}

function updateOAuthActionButtons(session) {
	const connected = Boolean(session && session.connected);
	for (const id of ["refreshOAuthSession", "refreshOAuthSessionSecondary"]) {
		const button = $(id);
		if (button) {
			button.disabled = !connected || !session.refreshCredentialPresent;
		}
	}
	const start = $("startOAuthFromAccount");
	if (start) {
		start.disabled = connected;
	}
	const disconnect = $("disconnectOAuth");
	if (disconnect) {
		disconnect.disabled = !connected;
		if (!disconnectConfirmationPending) {
			disconnect.textContent = "Desconectar Blaze";
		}
	}
}

function markOAuthSessionUnavailable() {
	currentOAuthSession = null;
	setText("oauthAccountStatus", "indisponivel", "warn");
	setText("oauthProfileStatus", "perfil indisponivel", "warn");
	setText("oauthConnectedLabel", "sessao indisponivel", "warn");
	setText("oauthAccountName", "-");
	setText("oauthAccountId", "-");
	setText("oauthTokenLabel", "-");
	setText("oauthRefreshLabel", "-");
	setText("oauthExpiresAt", "-");
	setText("oauthProfileLabel", "-");
	setText("oauthLastProfileSync", "-");
	setText("oauthNextAction", "-");
	setText("oauthAccountHelp", "Nao foi possivel ler a sessao OAuth local.");
	updateOAuthActionButtons({connected: false, refreshCredentialPresent: false});
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

async function loadSetup() {
	const setup = await request("/api/blaze/setup");
	renderSetup(setup);
	return setup;
}

function renderSetup(setup) {
	currentSetup = setup;
	const scopes = Array.isArray(setup.requestedScopes) ? setup.requestedScopes : [];
	const tokenLabel = setup.tokenPresent
		? `token usuario: presente${setup.tokenExpiredOrUnknown ? " / expirado ou desconhecido" : ""}`
		: "token usuario: ausente";

	setText("setupClientId", setup.clientIdConfigured ? "configurado" : "ausente", tone(setup.clientIdConfigured));
	setText("setupClientIdMasked", setup.clientIdMasked ? `id: ${setup.clientIdMasked}` : "id nao configurado");
	setText("setupClientCredential", `Client Secret: ${setup.clientCredentialConfigured ? "configurado" : "ausente"}`);
	setText("setupRedirectStatus", setup.redirectUriConfigured ? "cadastravel" : "ausente", tone(setup.redirectUriConfigured));
	setText("setupOAuthReady", setup.oauthStartReady ? "pronto para iniciar" : "configuracao incompleta", tone(setup.oauthStartReady));
	setText("setupToken", tokenLabel, tone(setup.tokenPresent));
	setText("setupRefreshCredential", `credencial renovacao: ${yesNo(setup.refreshCredentialPresent)}`);
	setText("oauthProfileStatus", setup.profilePresent ? "perfil sincronizado" : "perfil ausente", tone(setup.profilePresent));
	setText("setupRequestedScopes", scopes.length ? scopes.join(", ") : "nenhum", scopes.length ? "ok" : "warn");
	setText("setupChannel", setup.monitoredChannelConfigured ? "configurado" : "nao configurado", tone(setup.monitoredChannelConfigured));
	setText("setupChannelMasked", setup.monitoredChannel ? `canal: ${setup.monitoredChannel}` : "preencha quando testar canal real");
	setText("setupEvents", setup.eventsConfigReady ? "pre-requisitos ok" : "aguardando OAuth/canal", tone(setup.eventsConfigReady));

	$("setupRedirectValue").textContent = setup.redirectUri || "-";
	$("setupEnvExample").textContent = setup.envExample || "Sem exemplo disponivel.";

	renderChecklist(setup.checklist || []);
	renderScopes(setup.recommendedScopes || []);
	renderNextSteps(setup.nextSteps || []);
	applyDocsLinks(setup.docsLinks || []);
}

function renderChecklist(items) {
	const list = $("setupChecklist");
	list.textContent = "";
	if (!items.length) {
		list.appendChild(listItem("Nenhum item recebido."));
		return;
	}
	for (const item of items) {
		const li = document.createElement("li");
		const pill = document.createElement("span");
		const ok = item.status === "ok";
		pill.className = `status-pill ${ok ? "ok" : "warn"}`;
		pill.textContent = ok ? "ok" : "falta";
		li.appendChild(pill);
		li.appendChild(document.createTextNode(`${item.label}: ${item.help}`));
		list.appendChild(li);
	}
}

function renderScopes(scopes) {
	const list = $("setupScopes");
	list.textContent = "";
	if (!scopes.length) {
		list.appendChild(listItem("Nenhum scope recomendado recebido."));
		return;
	}
	for (const scope of scopes) {
		const phase = scope.requiredNow ? "agora" : scope.phase;
		list.appendChild(listItem(`${scope.name} - ${phase}: ${scope.reason}`));
	}
}

function renderNextSteps(steps) {
	const list = $("setupNextSteps");
	list.textContent = "";
	if (!steps.length) {
		list.appendChild(listItem("Setup sem pendencias imediatas."));
		return;
	}
	for (const step of steps) {
		list.appendChild(listItem(step));
	}
}

function listItem(text) {
	const li = document.createElement("li");
	li.textContent = text;
	return li;
}

function applyDocsLinks(docsLinks) {
	const byLabel = Object.fromEntries(docsLinks.map((link) => [link.label, link.url]));
	setHref("appSetupDocs", byLabel["App Setup"]);
	setHref("oauthDocs", byLabel["OAuth"]);
	setHref("scopesDocs", byLabel["Scopes"]);
	setHref("eventsDocs", byLabel["Events"]);
}

function setHref(id, href) {
	if (href) {
		$(id).href = href;
	}
}

function setupSummary(setup) {
	return {
		clientIdConfigured: setup.clientIdConfigured,
		clientCredentialConfigured: setup.clientCredentialConfigured,
		redirectUriConfigured: setup.redirectUriConfigured,
		requestedScopes: setup.requestedScopes,
		tokenPresent: setup.tokenPresent,
		refreshCredentialPresent: setup.refreshCredentialPresent,
		oauthConnected: setup.oauthConnected,
		profilePresent: setup.profilePresent,
		nextRecommendedAction: setup.nextRecommendedAction,
		monitoredChannelConfigured: setup.monitoredChannelConfigured,
		eventsConfigReady: setup.eventsConfigReady,
		oauthStartReady: setup.oauthStartReady,
		missingItems: (setup.missingItems || []).map((item) => item.label)
	};
}

function markSetupUnavailable() {
	currentSetup = null;
	setText("setupClientId", "indisponivel", "warn");
	setText("setupClientIdMasked", "-");
	setText("setupClientCredential", "Client Secret: indisponivel");
	setText("setupRedirectStatus", "indisponivel", "warn");
	setText("setupOAuthReady", "indisponivel", "warn");
	setText("setupToken", "token usuario: indisponivel", "warn");
	setText("setupRefreshCredential", "credencial renovacao: indisponivel");
	setText("setupRequestedScopes", "indisponivel", "warn");
	setText("setupChannel", "indisponivel", "warn");
	setText("setupChannelMasked", "-");
	setText("setupEvents", "indisponivel", "warn");
	$("setupRedirectValue").textContent = "-";
	$("setupEnvExample").textContent = "Setup indisponivel.";
	renderChecklist([]);
	renderScopes([]);
	renderNextSteps([]);
}

async function copyText(title, value) {
	if (!value) {
		log(title, "Nada para copiar.", true);
		return;
	}
	try {
		if (navigator.clipboard && window.isSecureContext) {
			await navigator.clipboard.writeText(value);
		}
		else {
			const area = document.createElement("textarea");
			area.value = value;
			area.setAttribute("readonly", "");
			area.style.position = "fixed";
			area.style.left = "-9999px";
			document.body.appendChild(area);
			area.select();
			document.execCommand("copy");
			document.body.removeChild(area);
		}
		log(title, "Copiado para a area de transferencia.");
	}
	catch (error) {
		log(title, "Nao foi possivel copiar automaticamente. Selecione o texto no painel.", true);
	}
}

function setOAuthHelp(message, href) {
	const nodes = [$("oauthHelp"), $("setupOAuthHelp")].filter(Boolean);
	for (const node of nodes) {
		node.textContent = message;
		if (href) {
			node.appendChild(document.createTextNode(" "));
			const link = document.createElement("a");
			link.href = href;
			link.target = "_blank";
			link.rel = "noreferrer";
			link.textContent = "Abrir autorizacao Blaze";
			node.appendChild(link);
		}
	}
}

function setOAuthButtonsDisabled(disabled) {
	for (const id of ["startOAuth", "startOAuthFromSetup", "startOAuthFromAccount"]) {
		const button = $(id);
		if (button) {
			button.disabled = disabled;
		}
	}
}

function markStatusUnavailable() {
	setText("appStatus", "indisponivel", "warn");
	setText("versionStatus", "-");
	setText("javaStatus", "-");
	setText("oauthConfigured", "indisponivel", "warn");
	setText("tokenStatus", "indisponivel", "warn");
	setText("refreshCredentialStatus", "refresh credential: indisponivel");
	setText("oauthAccountStatus", "indisponivel", "warn");
	setText("oauthProfileStatus", "perfil indisponivel", "warn");
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
	const [health, status, events, overlays, setup, oauthSession] = await Promise.all([
		safeLoad("Health indisponivel", loadHealth, () => setText("backendStatus", "offline", "bad")),
		safeLoad("Status indisponivel", loadStatus, markStatusUnavailable),
		safeLoad("Events indisponivel", loadEventsStatus, markEventsUnavailable),
		safeLoad("Overlays indisponiveis", loadOverlayDemo, markOverlayUnavailable),
		safeLoad("Setup indisponivel", loadSetup, markSetupUnavailable),
		safeLoad("OAuth session indisponivel", loadOAuthSession, markOAuthSessionUnavailable)
	]);

	log("Status atualizado", {
		health: health.ok ? health.value : "indisponivel",
		status: status.ok ? status.value : "indisponivel",
		events: events.ok ? events.value : "indisponivel",
		overlayProfiles: overlays.ok ? overlays.value.profiles.length : "indisponivel",
		overlays: overlays.ok ? overlays.value.overlays.length : "indisponivel",
		setup: setup.ok ? setupSummary(setup.value) : "indisponivel",
		oauthSession: oauthSession.ok ? oauthSessionSummary(oauthSession.value) : "indisponivel"
	});
}

function oauthSessionSummary(session) {
	return {
		connected: session.connected,
		tokenPresent: session.tokenPresent,
		refreshCredentialPresent: session.refreshCredentialPresent,
		profilePresent: session.profilePresent,
		accountId: session.profile && session.profile.id ? maskIdentifier(session.profile.id) : null,
		nextRecommendedAction: session.nextRecommendedAction
	};
}

async function startOAuth() {
	setOAuthButtonsDisabled(true);
	setOAuthHelp("Iniciando OAuth...");
	try {
		const response = await request("/api/blaze/oauth/start", {method: "POST"});
		if (response.authorizationUrl) {
			setOAuthHelp("OAuth iniciado. Finalize a autorizacao na janela da Blaze.");
			window.open(response.authorizationUrl, "_blank", "noopener,noreferrer");
		}
		log("OAuth start", {
			authorizationUrlPresent: Boolean(response.authorizationUrl),
			scopes: response.scopes || []
		});
	}
	catch (error) {
		const message = error.status === 503
			? "OAuth nao configurado. Confira BLAZE_CLIENT_ID, BLAZE_CLIENT_SECRET e BLAZE_REDIRECT_URI no backend."
			: error.message;
		setOAuthHelp(message);
		log("OAuth start", error.body || message, true);
	}
	finally {
		setOAuthButtonsDisabled(false);
	}
}

async function refreshOAuthSessionAction() {
	for (const id of ["refreshOAuthSession", "refreshOAuthSessionSecondary"]) {
		const button = $(id);
		if (button) {
			button.disabled = true;
		}
	}
	setText("oauthAccountHelp", "Atualizando sessao Blaze...");
	try {
		const response = await request("/api/blaze/oauth/refresh", {method: "POST"});
		log("OAuth refresh", response);
		setText("oauthAccountHelp", response.message || "Sessao atualizada.");
		await loadAll();
	}
	catch (error) {
		const message = error.status === 503
			? "Nao ha credencial de renovacao disponivel ou a configuracao esta incompleta."
			: error.message;
		setText("oauthAccountHelp", message);
		log("OAuth refresh", error.body || message, true);
	}
	finally {
		updateOAuthActionButtons(currentOAuthSession || {connected: false, refreshCredentialPresent: false});
	}
}

async function disconnectOAuthAction() {
	if (!currentOAuthSession || !currentOAuthSession.connected) {
		return;
	}
	const now = Date.now();
	if (!disconnectConfirmationPending || now - disconnectConfirmationAt > 8000) {
		disconnectConfirmationPending = true;
		disconnectConfirmationAt = now;
		$("disconnectOAuth").textContent = "Confirmar desconexao";
		setText("oauthAccountHelp", "Clique novamente para desconectar a conta Blaze deste backend local.");
		return;
	}
	disconnectConfirmationPending = false;
	$("disconnectOAuth").disabled = true;
	setText("oauthAccountHelp", "Desconectando Blaze...");
	try {
		const response = await request("/api/blaze/oauth/disconnect", {method: "POST"});
		log("OAuth disconnect", response);
		setText("oauthAccountHelp", response.message || "Conta Blaze desconectada.");
		await loadAll();
	}
	catch (error) {
		const message = error.message || "Nao foi possivel desconectar.";
		setText("oauthAccountHelp", message);
		log("OAuth disconnect", error.body || message, true);
	}
	finally {
		updateOAuthActionButtons(currentOAuthSession || {connected: false, refreshCredentialPresent: false});
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
$("refreshSetup").addEventListener("click", async () => {
	const setup = await safeLoad("Setup indisponivel", loadSetup, markSetupUnavailable);
	if (setup.ok) {
		log("Setup atualizado", setupSummary(setup.value));
	}
});
$("startOAuth").addEventListener("click", startOAuth);
$("startOAuthFromSetup").addEventListener("click", startOAuth);
$("startOAuthFromAccount").addEventListener("click", startOAuth);
$("refreshOAuthSession").addEventListener("click", refreshOAuthSessionAction);
$("refreshOAuthSessionSecondary").addEventListener("click", refreshOAuthSessionAction);
$("disconnectOAuth").addEventListener("click", disconnectOAuthAction);
$("copyRedirectUri").addEventListener("click", () => copyText("Redirect URI", currentSetup && currentSetup.redirectUri));
$("copyScopes").addEventListener("click", () => {
	const scopes = currentSetup && Array.isArray(currentSetup.requestedScopes) ? currentSetup.requestedScopes.join(",") : "";
	copyText("Scopes", scopes);
});
$("copyEnvExample").addEventListener("click", () => copyText("Exemplo .env", currentSetup && currentSetup.envExample));
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


// --- Channel Config Panel ---
async function loadChannels() {
	try {
		const channels = await request("/api/blaze/channel");
		renderChannels(channels);
		return channels;
	}
	catch (error) {
		log("Channels", error.body || error.message, true);
		return [];
	}
}

function renderChannels(channels) {
	const grid = $("channelGrid");
	if (!channels.length) {
		grid.textContent = "";
		const card = document.createElement("article");
		card.className = "card channel-card";
		card.innerHTML = '<span class="label">Canais</span><strong>Nenhum canal configurado</strong><small>Crie um canal abaixo.</small>';
		grid.appendChild(card);
		return;
	}
	grid.textContent = "";
	for (const ch of channels) {
		const card = document.createElement("article");
		card.className = "card channel-card";
		const label = document.createElement("span");
		label.className = "label";
		label.textContent = (ch.platform || "blaze") + " canal";
		const name = document.createElement("strong");
		name.textContent = ch.name;
		const idLine = document.createElement("small");
		idLine.textContent = "ID: " + maskIdentifier(ch.channelId);
		const monLine = document.createElement("small");
		monLine.textContent = "Monitorado: " + (ch.monitored ? "sim" : "nao");
		const actions = document.createElement("div");
		actions.className = "actions";
		const deleteBtn = document.createElement("button");
		deleteBtn.className = "danger-button";
		deleteBtn.textContent = "Excluir";
		deleteBtn.addEventListener("click", () => deleteChannelAction(ch.id));
		actions.appendChild(deleteBtn);
		card.appendChild(label);
		card.appendChild(name);
		card.appendChild(idLine);
		card.appendChild(monLine);
		card.appendChild(actions);
		grid.appendChild(card);
	}
}

async function createChannelAction() {
	const name = $("channelName").value.trim();
	const channelId = $("channelIdInput").value.trim();
	const monitored = $("channelMonitored").checked;
	if (!name || !channelId) {
		setText("channelFormHelp", "Nome e Channel ID sao obrigatorios.");
		return;
	}
	try {
		setText("channelFormHelp", "Criando canal...");
		await request("/api/blaze/channel", {
			method: "POST",
			body: JSON.stringify({name, channelId, platform: "blaze", monitored})
		});
		setText("channelFormHelp", "Canal criado com sucesso.", "ok");
		$("channelName").value = "";
		$("channelIdInput").value = "";
		await loadChannels();
		log("Channel create", {name, channelId, monitored});
	}
	catch (error) {
		setText("channelFormHelp", error.message || "Erro ao criar canal.");
		log("Channel create", error.body || error.message, true);
	}
}

async function deleteChannelAction(id) {
	try {
		await request("/api/blaze/channel/" + encodeURIComponent(id), {method: "DELETE"});
		log("Channel delete", {id: id});
		await loadChannels();
	}
	catch (error) {
		log("Channel delete", error.body || error.message, true);
	}
}

// --- Events Panel ---
async function loadEventsPanel() {
	try {
		const [status, caps] = await Promise.all([
			request("/api/blaze/events/status"),
			request("/api/blaze/events/capabilities")
		]);
		renderEventsPanel(status, caps);
		await loadEventsLog();
		return {status, caps};
	}
	catch (error) {
		log("Events panel", error.body || error.message, true);
		return null;
	}
}

function renderEventsPanel(status, caps) {
	setText("eventsPanelRunning", status.runnerRunning ? "Rodando" : "Parado", status.runnerRunning ? "ok" : "warn");
	setText("eventsPanelSession", status.sessionId ? "Session: " + maskIdentifier(status.sessionId) : "Sem sessao");
	setText("eventsPanelCount", status.eventCount || 0);
	setText("eventsPanelLastReceived", status.lastEventReceivedAt ? "Ultimo: " + formatDate(status.lastEventReceivedAt) : "Nenhum evento ainda");
	const capEngine = caps && caps.engine ? caps.engine : {};
	setText("eventsPanelCapabilities", capEngine.simulated ? "Simulado" : "Real", capEngine.simulated ? "warn" : "ok");
}

async function loadEventsLog() {
	try {
		const filter = $("logEventTypeFilter").value.trim();
		const params = filter ? "?eventType=" + encodeURIComponent(filter) : "";
		const data = await request("/api/blaze/events/log" + params);
		renderEventsLog(data);
		return data;
	}
	catch (error) {
		log("Events log", error.body || error.message, true);
		return null;
	}
}

function renderEventsLog(data) {
	const pre = $("eventsLog");
	if (!data || !data.entries || !data.entries.length) {
		pre.textContent = "Nenhum evento registrado.";
		return;
	}
	const lines = data.entries.map(function(e) {
		const ts = formatDate(e.timestamp);
		return "[" + ts + "] " + (e.source || "-") + " | " + (e.eventType || "-") + " | " + (e.message || "-");
	});
	pre.textContent = lines.join("\n");
}

async function simulateEventAction() {
	const eventType = $("simulateEventType").value;
	const message = $("simulateMessage").value.trim();
	try {
		setText("simulateHelp", "Simulando evento...");
		const entry = await request("/api/blaze/events/simulate", {
			method: "POST",
			body: JSON.stringify({eventType: eventType, message: message || undefined})
		});
		setText("simulateHelp", "Evento simulado: " + entry.eventType, "ok");
		$("simulateMessage").value = "";
		log("Simulate", entry);
		await loadEventsLog();
	}
	catch (error) {
		setText("simulateHelp", error.message || "Erro ao simular.");
		log("Simulate", error.body || error.message, true);
	}
}

async function startEventsPanel() {
	try {
		const status = await request("/api/blaze/events/start", {method: "POST"});
		log("Start engine", status);
		await loadEventsPanel();
	}
	catch (error) {
		log("Start engine", error.body || error.message, true);
	}
}

async function stopEventsPanel() {
	try {
		const status = await request("/api/blaze/events/stop", {method: "POST"});
		log("Stop engine", status);
		await loadEventsPanel();
	}
	catch (error) {
		log("Stop engine", error.body || error.message, true);
	}
}

// Override loadAll to also load channels and events panel
const _originalLoadAll = loadAll;
async function loadAllExtended() {
	await _originalLoadAll();
	await Promise.all([
		safeLoad("Channels indisponiveis", loadChannels, function() {}),
		safeLoad("Events panel indisponivel", loadEventsPanel, function() {})
	]);
}

// Re-bind the main loadAll to the extended version
loadAll = loadAllExtended;

$("createChannel").addEventListener("click", createChannelAction);
$("refreshEventsPanel").addEventListener("click", loadEventsPanel);
$("eventsPanelStart").addEventListener("click", startEventsPanel);
$("eventsPanelStop").addEventListener("click", stopEventsPanel);
$("simulateEventBtn").addEventListener("click", simulateEventAction);
$("refreshLogBtn").addEventListener("click", loadEventsLog);
$("clearLogBtn").addEventListener("click", function() {
	$("eventsLog").textContent = "Log limpo (reinicie para novos eventos).";
	log("Log cleared", "Log visual limpo.");
});

loadAllExtended();
