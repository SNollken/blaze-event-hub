(function () {
	"use strict";

	const API_KEY = "dev-local-key";
	const DEMO_OVERLAY = "/overlay/demo-overlay-obs-mvp";
	const DEMO_MANIFEST = "/api/public/overlays/demo-overlay-obs-mvp/manifest";
	const SENSITIVE_WORDS = [
		"access" + "Token",
		"access" + "_token",
		"refresh" + "Token",
		"refresh" + "_token",
		"client" + "Secret",
		"client" + "_secret",
		"code" + "Verifier",
		"code" + "_verifier",
		"authorization" + "Url"
	];

	const sectionTitles = {
		overview: "Visao geral",
		account: "Conta Blaze",
		channel: "Canal monitorado",
		events: "Events",
		"live-events": "Live Events",
		alerts: "Alertas",
		giveaways: "Sorteios",
		overlays: "Overlays",
		settings: "Configuracoes",
		diagnostic: "Diagnostico"
	};

	const state = {
		data: {},
		loading: false,
		channelId: null
	};

	document.addEventListener("DOMContentLoaded", function () {
		wireNavigation();
		wireActions();
		activateSection(location.hash.replace("#", "") || "overview");
		loadDashboard();
	});

	function wireNavigation() {
		document.querySelectorAll("[data-section-target]").forEach(function (button) {
			button.addEventListener("click", function () {
				activateSection(button.dataset.sectionTarget);
			});
		});
		window.addEventListener("hashchange", function () {
			activateSection(location.hash.replace("#", "") || "overview");
		});
	}

	function activateSection(sectionId) {
		if (!sectionTitles[sectionId]) {
			sectionId = "overview";
		}
		document.querySelectorAll(".dashboard-section").forEach(function (section) {
			section.classList.toggle("active", section.id === sectionId);
		});
		document.querySelectorAll("[data-section-target]").forEach(function (button) {
			button.classList.toggle("active", button.dataset.sectionTarget === sectionId);
		});
		setText("page-title", sectionTitles[sectionId]);
		if (location.hash !== "#" + sectionId) {
			history.replaceState(null, "", "#" + sectionId);
		}
	}

	function wireActions() {
		byId("refresh-dashboard").addEventListener("click", loadDashboard);
		byId("live-filter").addEventListener("change", function () {
			renderLiveEvents();
		});
		byId("channel-form").addEventListener("submit", submitChannel);
		byId("giveaway-form").addEventListener("submit", submitGiveaway);
		document.body.addEventListener("click", function (event) {
			const target = event.target.closest("[data-action]");
			if (!target) {
				return;
			}
			runAction(target);
		});
	}

	async function loadDashboard() {
		if (state.loading) {
			return;
		}
		state.loading = true;
		setBusy(true);
		setText("sidebar-updated", "Atualizando dados");
		const endpoints = {
			health: get("/api/health"),
			status: get("/api/status"),
			setup: get("/api/blaze/setup"),
			session: get("/api/blaze/oauth/session"),
			eventsStatus: get("/api/blaze/events/status"),
			eventsLog: get("/api/blaze/events/log?limit=8"),
			eventsCapabilities: get("/api/blaze/events/capabilities"),
			channel: get("/api/blaze/channel"),
			liveEvents: get("/api/live-events"),
			liveStats: get("/api/live-events/stats"),
			alertStats: get("/api/alerts/stats"),
			activeAlerts: get("/api/alerts/active"),
			alertHistory: get("/api/alerts/history"),
			alertRules: get("/api/alerts/rules"),
			giveaways: get("/api/giveaways"),
			giveawayStats: get("/api/giveaways/stats"),
			overlayProfiles: get("/api/overlay-profiles"),
			overlayManifest: get(DEMO_MANIFEST)
		};

		const names = Object.keys(endpoints);
		const results = await Promise.all(names.map(function (name) {
			return endpoints[name];
		}));
		names.forEach(function (name, index) {
			state.data[name] = results[index];
		});
		state.loading = false;
		setBusy(false);
		renderAll();
		setText("sidebar-updated", "Atualizado " + formatTime(new Date()));
	}

	async function get(path) {
		try {
			const result = await request(path);
			return { ok: true, status: result.status, data: result.data };
		}
		catch (error) {
			return friendlyFailure(error);
		}
	}

	async function request(path, options) {
		const opts = options || {};
		const headers = {
			Accept: "application/json"
		};
		if (path.startsWith("/api/") && !path.startsWith("/api/public/")) {
			headers["X-Nollen-Api-Key"] = API_KEY;
		}
		if (opts.body) {
			headers["Content-Type"] = "application/json";
		}
		const response = await fetch(path, Object.assign({
			cache: "no-store",
			headers: headers
		}, opts, {
			headers: Object.assign(headers, opts.headers || {})
		}));
		const text = await response.text();
		if (!response.ok) {
			throw {
				status: response.status,
				text: text,
				message: response.statusText || "Falha na requisicao"
			};
		}
		return {
			status: response.status,
			data: parseBody(response, text)
		};
	}

	function parseBody(response, text) {
		if (!text) {
			return null;
		}
		const contentType = response.headers.get("content-type") || "";
		if (contentType.includes("application/json")) {
			return JSON.parse(text);
		}
		return text;
	}

	function friendlyFailure(error) {
		const status = Number(error.status || 0);
		let label = "Erro ao carregar";
		if (status === 404) {
			label = "Nao disponivel nesta versao";
		}
		else if (status === 401 || status === 403) {
			label = "Acesso protegido pela API key";
		}
		else if (status === 0) {
			label = "Backend indisponivel";
		}
		return {
			ok: false,
			status: status,
			error: label
		};
	}

	function renderAll() {
		renderOverview();
		renderAccount();
		renderChannel();
		renderEvents();
		renderLiveEvents();
		renderAlerts();
		renderGiveaways();
		renderOverlays();
		renderSettings();
		renderDiagnostic();
	}

	function renderOverview() {
		const status = state.data.status;
		const health = state.data.health;
		const session = state.data.session;
		const events = state.data.eventsStatus;
		const overlays = state.data.overlayProfiles;
		const statusData = dataOf(status) || {};
		const setupData = dataOf(state.data.setup) || {};
		const healthOk = isOk(health) && String(dataOf(health).status || "").toLowerCase() === "ok";
		setBadge("sidebar-health", healthOk ? "online" : "pendente", healthOk ? "ok" : "pending");
		setBadge("overview-state", status.ok ? "online" : status.error, status.ok ? "ok" : "error");
		const metrics = [
			["Backend", healthOk ? "Online" : "Offline", healthOk ? "ok" : "error"],
			["App", statusData.appName || setupData.appName || "NollenBlaze", "muted"],
			["Versao", statusData.version || "dev", "muted"],
			["Java", statusData.javaVersion || "Sem dados", "muted"],
			["OAuth", flag(statusData.oauthConnected || valueOf(session, "connected"), "Conectado", "Pendente"), statusData.oauthConnected || valueOf(session, "connected") ? "ok" : "pending"],
			["Token", flag(statusData.tokenPresent || valueOf(session, "tokenPresent"), "Presente", "Pendente"), statusData.tokenPresent || valueOf(session, "tokenPresent") ? "ok" : "pending"],
			["Refresh", flag(statusData.refreshCredentialPresent || valueOf(session, "refreshCredentialPresent"), "Configurado", "Pendente"), statusData.refreshCredentialPresent || valueOf(session, "refreshCredentialPresent") ? "ok" : "pending"],
			["Canal", flag(statusData.monitoredChannelConfigured || setupData.monitoredChannelConfigured, "Configurado", "Pendente"), statusData.monitoredChannelConfigured || setupData.monitoredChannelConfigured ? "ok" : "pending"],
			["Events", eventStateText(dataOf(events)), valueOf(events, "runnerRunning") ? "ok" : "pending"],
			["Overlays", String(statusData.overlaysCount ?? countOf(overlays)), "muted"],
			["Profiles", String(statusData.activeProfilesCount ?? countOf(overlays)), "muted"],
			["Uptime", formatDuration(statusData.uptimeSeconds), "muted"]
		];
		renderMetrics("overview-metrics", metrics);
		renderEventsPreview();
		renderOverviewModules();
	}

	function renderMetrics(id, metrics) {
		const box = clear(id);
		metrics.forEach(function (metric) {
			const card = node("article", "metric-card");
			card.appendChild(node("span", "", metric[0]));
			card.appendChild(node("strong", "", safeText(metric[1])));
			card.appendChild(makeBadge(metric[1], metric[2]));
			box.appendChild(card);
		});
	}

	function renderEventsPreview() {
		const result = state.data.eventsLog;
		const entries = entriesFromLog(result);
		setBadge("events-log-state", result.ok ? String(entries.length) + " itens" : result.error, result.ok ? "muted" : "error");
		renderList("overview-events-list", entries.slice(0, 5), function (entry) {
			return {
				title: entry.eventType || "Evento",
				detail: [entry.message, formatDate(entry.timestamp)].filter(Boolean).join(" - "),
				badge: entry.source || "events",
				tone: "muted"
			};
		}, "Nenhum evento recente encontrado.");
	}

	function renderOverviewModules() {
		const rows = [
			["Events runner", eventStateText(dataOf(state.data.eventsStatus)), valueOf(state.data.eventsStatus, "runnerRunning") ? "ok" : "pending"],
			["Events client", valueOf(state.data.eventsStatus, "clientRunning") ? "Conectado" : "Desconectado", valueOf(state.data.eventsStatus, "clientRunning") ? "ok" : "pending"],
			["Live Events", state.data.liveEvents.ok ? countOf(state.data.liveEvents) + " eventos" : state.data.liveEvents.error, state.data.liveEvents.ok ? "ok" : "error"],
			["Alertas", state.data.alertStats.ok ? String(valueOf(state.data.alertStats, "totalAlerts") || 0) + " historico" : state.data.alertStats.error, state.data.alertStats.ok ? "ok" : "error"],
			["Sorteios", state.data.giveawayStats.ok ? String(valueOf(state.data.giveawayStats, "totalGiveaways") || 0) + " sorteios" : state.data.giveawayStats.error, state.data.giveawayStats.ok ? "ok" : "error"],
			["Manifest OBS", state.data.overlayManifest.ok ? "Disponivel" : state.data.overlayManifest.error, state.data.overlayManifest.ok ? "ok" : "error"]
		];
		renderStatusRows("overview-modules", rows);
	}

	function renderAccount() {
		const session = state.data.session;
		const data = dataOf(session) || {};
		setBadge("account-state", session.ok ? (data.connected ? "conectado" : "pendente") : session.error, session.ok ? (data.connected ? "ok" : "pending") : "error");
		setBadge("session-state", data.connected ? "conectado" : "sem conexao", data.connected ? "ok" : "pending");
		renderStatusRows("account-summary", [
			["OAuth", flag(data.connected, "Conectado", "Pendente"), data.connected ? "ok" : "pending"],
			["Token", flag(data.tokenPresent, "Presente", "Ausente"), data.tokenPresent ? "ok" : "pending"],
			["Refresh credential", flag(data.refreshCredentialPresent, "Configurada", "Ausente"), data.refreshCredentialPresent ? "ok" : "pending"],
			["Perfil", flag(data.profilePresent, "Sincronizado", "Indisponivel"), data.profilePresent ? "ok" : "pending"],
			["Expiracao", data.expiresAt ? formatDate(data.expiresAt) : "Sem dados", "muted"],
			["Proxima acao", data.nextRecommendedAction || "Sem recomendacao", "muted"]
		]);
		renderProfile(data.profile);
	}

	function renderProfile(profile) {
		const box = clear("profile-summary");
		if (!profile) {
			box.className = "empty-state";
			box.textContent = "Nenhum perfil conectado nesta sessao.";
			return;
		}
		box.className = "status-list";
		renderStatusRows("profile-summary", [
			["Nome", profile.displayName || profile.username || "Sem nome", "muted"],
			["Usuario", profile.username || "Sem usuario", "muted"],
			["ID publico", profile.id || "Sem ID", "muted"]
		]);
	}

	function renderChannel() {
		const result = state.data.channel;
		const channels = arrayOf(result);
		const monitored = channels.find(function (item) {
			return item.monitored;
		}) || channels[0];
		state.channelId = monitored ? monitored.id : null;
		setBadge("channel-state", result.ok ? (monitored ? "configurado" : "pendente") : result.error, result.ok ? (monitored ? "ok" : "pending") : "error");
		renderStatusRows("channel-summary", [
			["Endpoint", result.ok ? "Disponivel" : result.error, result.ok ? "ok" : "error"],
			["Nome", monitored ? monitored.name : "Nenhum canal configurado", monitored ? "ok" : "pending"],
			["Channel ID", monitored ? monitored.channelId : "Pendente", monitored ? "ok" : "pending"],
			["Monitorado", monitored && monitored.monitored ? "Sim" : "Nao", monitored && monitored.monitored ? "ok" : "pending"]
		]);
		if (monitored) {
			byId("channel-name").value = monitored.name || "";
			byId("channel-id").value = monitored.channelId || "";
		}
	}

	function renderEvents() {
		const result = state.data.eventsStatus;
		const data = dataOf(result) || {};
		setBadge("events-state", result.ok ? eventStateText(data) : result.error, result.ok ? (data.runnerRunning ? "ok" : "pending") : "error");
		renderStatusRows("events-summary", [
			["Runner", data.runnerRunning ? "Ativo" : "Parado", data.runnerRunning ? "ok" : "pending"],
			["Client", data.clientRunning ? "Conectado" : "Desconectado", data.clientRunning ? "ok" : "pending"],
			["Ultimo evento", data.lastMessageType || "Nenhum evento recebido", data.lastMessageType ? "ok" : "pending"],
			["Ultimo recebimento", data.lastEventReceivedAt ? formatDate(data.lastEventReceivedAt) : "Sem dados", "muted"],
			["Total no log", String(data.eventCount || 0), "muted"],
			["Engine", data.engineAvailable === false ? "Indisponivel" : "Disponivel", data.engineAvailable === false ? "error" : "ok"]
		]);
		const entries = entriesFromLog(state.data.eventsLog);
		setText("events-log-count", String(entries.length) + " itens");
		renderList("events-log-list", entries, function (entry) {
			return {
				title: entry.eventType || "Evento",
				detail: [entry.message, formatDate(entry.timestamp)].filter(Boolean).join(" - "),
				badge: entry.source || "log",
				tone: "muted"
			};
		}, "Log vazio nesta sessao.");
	}

	function renderLiveEvents() {
		const result = state.data.liveEvents;
		const events = arrayOf(result);
		const filter = byId("live-filter").value;
		const visible = filter ? events.filter(function (event) {
			return String(event.status || "") === filter;
		}) : events;
		setBadge("live-events-state", result.ok ? String(events.length) + " eventos" : result.error, result.ok ? "ok" : "error");
		renderTable("live-events-list", ["Tipo", "Status", "Origem", "Horario"], visible.map(function (event) {
			return [
				event.type || "Evento",
				event.status || "Sem status",
				event.source || "Sem origem",
				formatDate(event.timestamp)
			];
		}), result.ok ? "Nenhum Live Event captado ainda." : result.error);
	}

	function renderAlerts() {
		const active = arrayOf(state.data.activeAlerts);
		const history = arrayOf(state.data.alertHistory);
		const stats = dataOf(state.data.alertStats) || {};
		setBadge("alerts-state", state.data.alertStats.ok ? String(stats.totalAlerts || 0) + " alertas" : state.data.alertStats.error, state.data.alertStats.ok ? "ok" : "error");
		setText("active-alerts-count", String(active.length));
		renderList("active-alerts-list", active, function (alert) {
			return {
				title: alert.ruleName || alert.eventType || "Alerta",
				detail: [alert.message, formatDate(alert.triggeredAt)].filter(Boolean).join(" - "),
				badge: alert.acknowledged ? "visto" : "ativo",
				tone: alert.acknowledged ? "muted" : "pending"
			};
		}, "Nenhum alerta ativo.");
		renderList("alert-history-list", history.slice(0, 6), function (alert) {
			return {
				title: alert.ruleName || alert.eventType || "Alerta",
				detail: [alert.message, formatDate(alert.triggeredAt)].filter(Boolean).join(" - "),
				badge: alert.acknowledged ? "visto" : "pendente",
				tone: alert.acknowledged ? "muted" : "pending"
			};
		}, "Historico vazio.");
	}

	function renderGiveaways() {
		const result = state.data.giveaways;
		const giveaways = arrayOf(result);
		setBadge("giveaways-state", result.ok ? String(giveaways.length) + " sorteios" : result.error, result.ok ? "ok" : "error");
		setText("giveaways-count", String(giveaways.length));
		renderList("giveaways-list", giveaways, function (giveaway) {
			return {
				title: giveaway.title || "Sorteio",
				detail: [String(giveaway.entryCount || 0) + " participantes", giveaway.description].filter(Boolean).join(" - "),
				badge: giveaway.status || "DRAFT",
				tone: giveaway.status === "OPEN" ? "ok" : "muted",
				actions: giveawayActions(giveaway)
			};
		}, "Nenhum sorteio criado.");
	}

	function giveawayActions(giveaway) {
		const actions = [];
		if (giveaway.status === "DRAFT" || giveaway.status === "CLOSED") {
			actions.push({ label: "Abrir", action: "giveaway-open", id: giveaway.id });
		}
		if (giveaway.status === "OPEN") {
			actions.push({ label: "Fechar", action: "giveaway-close", id: giveaway.id });
			actions.push({ label: "Sortear", action: "giveaway-draw", id: giveaway.id });
		}
		return actions;
	}

	function renderOverlays() {
		const profiles = arrayOf(state.data.overlayProfiles);
		const manifest = state.data.overlayManifest;
		setBadge("overlays-state", manifest.ok ? "manifest ok" : manifest.error, manifest.ok ? "ok" : "error");
		renderStatusRows("overlay-summary", [
			["Runtime OBS demo", DEMO_OVERLAY, "ok"],
			["Manifest publico", manifest.ok ? "Disponivel" : manifest.error, manifest.ok ? "ok" : "error"],
			["Fundo transparente", "Sim", "ok"],
			["Profiles", String(profiles.length), "muted"]
		]);
		renderList("overlay-profiles-list", profiles, function (profile) {
			return {
				title: profile.name || "Profile",
				detail: profile.description || "Sem descricao",
				badge: "profile",
				tone: "muted"
			};
		}, "Nenhum profile retornado pelo endpoint.");
	}

	function renderSettings() {
		const setup = dataOf(state.data.setup) || {};
		setBadge("settings-state", state.data.setup.ok ? "setup carregado" : state.data.setup.error, state.data.setup.ok ? "ok" : "error");
		renderStatusRows("settings-summary", [
			["Redirect URI", setup.redirectUri || "Nao configurado", setup.redirectUri ? "ok" : "pending"],
			["Scopes", Array.isArray(setup.requestedScopes) ? setup.requestedScopes.join(", ") : "Sem dados", setup.requestedScopes ? "ok" : "pending"],
			["Ambiente", setup.environment || "local", "muted"],
			["Client ID", setup.clientIdConfigured ? "Configurado" : "Pendente", setup.clientIdConfigured ? "ok" : "pending"],
			["Credencial do app", setup.clientCredentialConfigured ? "Configurada" : "Pendente", setup.clientCredentialConfigured ? "ok" : "pending"],
			["Events pronto", setup.eventsConfigReady ? "Sim" : "Nao", setup.eventsConfigReady ? "ok" : "pending"]
		]);
		const checklist = Array.isArray(setup.checklist) ? setup.checklist : [];
		renderList("setup-checklist", checklist, function (item) {
			return {
				title: item.name || "Item",
				detail: item.help || item.status || "",
				badge: item.configured ? "ok" : "pendente",
				tone: item.configured ? "ok" : "pending"
			};
		}, "Checklist indisponivel nesta versao.");
	}

	function renderDiagnostic() {
		const endpoints = [
			["GET /", true, "Shell do dashboard"],
			["GET /dashboard", true, "Shell do dashboard"],
			["GET /api/health", state.data.health.ok, messageFor(state.data.health)],
			["GET /api/status", state.data.status.ok, messageFor(state.data.status)],
			["GET /api/blaze/setup", state.data.setup.ok, messageFor(state.data.setup)],
			["GET /api/blaze/oauth/session", state.data.session.ok, messageFor(state.data.session)],
			["GET /api/blaze/events/status", state.data.eventsStatus.ok, messageFor(state.data.eventsStatus)],
			["GET /api/blaze/events/log", state.data.eventsLog.ok, messageFor(state.data.eventsLog)],
			["GET /api/blaze/channel", state.data.channel.ok, messageFor(state.data.channel)],
			["GET /api/live-events", state.data.liveEvents.ok, messageFor(state.data.liveEvents)],
			["GET /api/alerts/stats", state.data.alertStats.ok, messageFor(state.data.alertStats)],
			["GET /api/giveaways", state.data.giveaways.ok, messageFor(state.data.giveaways)],
			["GET /api/overlay-profiles", state.data.overlayProfiles.ok, messageFor(state.data.overlayProfiles)],
			["GET " + DEMO_MANIFEST, state.data.overlayManifest.ok, messageFor(state.data.overlayManifest)],
			["GET " + DEMO_OVERLAY, true, "Runtime publico"]
		];
		const box = clear("diagnostic-grid");
		endpoints.forEach(function (item) {
			const card = node("article", "diagnostic-card");
			card.appendChild(node("h3", "", item[0]));
			card.appendChild(makeBadge(item[1] ? "Disponivel" : item[2], item[1] ? "ok" : "error"));
			card.appendChild(node("span", "", safeText(item[2])));
			box.appendChild(card);
		});
	}

	async function runAction(button) {
		const action = button.dataset.action;
		if (button.disabled) {
			return;
		}
		button.disabled = true;
		try {
			if (action === "oauth-start") {
				const result = await request("/api/blaze/oauth/start", { method: "POST" });
				if (result.data && result.data.authorizationUrl) {
					window.location.assign(result.data.authorizationUrl);
					return;
				}
				toast("OAuth", "URL de autorizacao indisponivel.", true);
			}
			else if (action === "oauth-refresh") {
				await request("/api/blaze/oauth/refresh", { method: "POST" });
				toast("Conta Blaze", "Sessao atualizada.");
				await loadDashboard();
			}
			else if (action === "oauth-disconnect") {
				await request("/api/blaze/oauth/disconnect", { method: "POST" });
				toast("Conta Blaze", "Sessao local desconectada.");
				await loadDashboard();
			}
			else if (action === "reload-session" || action === "refresh-diagnostic") {
				await loadDashboard();
			}
			else if (action === "events-start") {
				await request("/api/blaze/events/start", { method: "POST" });
				toast("Events", "Comando de inicio enviado.");
				await loadDashboard();
			}
			else if (action === "events-stop") {
				await request("/api/blaze/events/stop", { method: "POST" });
				toast("Events", "Comando de parada enviado.");
				await loadDashboard();
			}
			else if (action === "events-simulate") {
				await request("/api/blaze/events/simulate", {
					method: "POST",
					body: JSON.stringify({ eventType: "channel.chat.message", message: "Evento simulado pelo dashboard MVP" })
				});
				toast("Events", "Evento simulado.");
				await loadDashboard();
			}
			else if (action === "live-simulate") {
				await request("/api/live-events/simulate", { method: "POST" });
				toast("Live Events", "Evento de intake simulado.");
				await loadDashboard();
			}
			else if (action === "alert-simulate") {
				await request("/api/alerts/evaluate", {
					method: "POST",
					body: JSON.stringify({ eventType: button.dataset.event, payload: { message: "Simulacao do dashboard MVP", amount: 10, viewerCount: 5 } })
				});
				toast("Alertas", "Simulacao enviada.");
				await loadDashboard();
			}
			else if (action === "giveaway-open" || action === "giveaway-close" || action === "giveaway-draw") {
				const suffix = action.replace("giveaway-", "");
				const path = "/api/giveaways/" + encodeURIComponent(button.dataset.id) + "/" + suffix;
				await request(path, { method: "POST" });
				toast("Sorteios", "Acao executada.");
				await loadDashboard();
			}
			else if (action === "copy-overlay-link") {
				await navigator.clipboard.writeText(window.location.origin + DEMO_OVERLAY);
				toast("Overlays", "Link copiado.");
			}
			else if (action === "clear-channel") {
				await clearChannel();
			}
		}
		catch (error) {
			toast("Nao foi possivel concluir", friendlyFailure(error).error, true);
		}
		finally {
			button.disabled = false;
		}
	}

	async function submitChannel(event) {
		event.preventDefault();
		const name = byId("channel-name").value.trim() || "Canal monitorado";
		const channelId = byId("channel-id").value.trim();
		if (!channelId) {
			toast("Canal monitorado", "Informe o Channel ID.", true);
			return;
		}
		const payload = {
			name: name,
			channelId: channelId,
			platform: "blaze",
			monitored: true
		};
		try {
			const method = state.channelId ? "PUT" : "POST";
			const path = state.channelId ? "/api/blaze/channel/" + encodeURIComponent(state.channelId) : "/api/blaze/channel";
			await request(path, { method: method, body: JSON.stringify(payload) });
			toast("Canal monitorado", "Configuracao salva.");
			await loadDashboard();
		}
		catch (error) {
			toast("Canal monitorado", friendlyFailure(error).error, true);
		}
	}

	async function clearChannel() {
		if (!state.channelId) {
			byId("channel-name").value = "";
			byId("channel-id").value = "";
			toast("Canal monitorado", "Nenhum canal para limpar.");
			return;
		}
		try {
			await request("/api/blaze/channel/" + encodeURIComponent(state.channelId), { method: "DELETE" });
			state.channelId = null;
			byId("channel-name").value = "";
			byId("channel-id").value = "";
			toast("Canal monitorado", "Canal removido.");
			await loadDashboard();
		}
		catch (error) {
			toast("Canal monitorado", friendlyFailure(error).error, true);
		}
	}

	async function submitGiveaway(event) {
		event.preventDefault();
		const title = byId("giveaway-title").value.trim();
		const maxEntries = Number(byId("giveaway-limit").value || 50);
		if (!title) {
			toast("Sorteios", "Informe um titulo.", true);
			return;
		}
		try {
			await request("/api/giveaways", {
				method: "POST",
				body: JSON.stringify({ title: title, description: "Criado pelo Dashboard Shell MVP", maxEntries: maxEntries })
			});
			byId("giveaway-title").value = "";
			toast("Sorteios", "Sorteio criado.");
			await loadDashboard();
		}
		catch (error) {
			toast("Sorteios", friendlyFailure(error).error, true);
		}
	}

	function renderStatusRows(id, rows) {
		const box = clear(id);
		rows.forEach(function (row) {
			const item = node("div", "status-row");
			item.appendChild(node("span", "", row[0]));
			item.appendChild(makeBadge(row[1], row[2]));
			box.appendChild(item);
		});
	}

	function renderList(id, items, mapper, emptyText) {
		const box = clear(id);
		if (!items || items.length === 0) {
			box.appendChild(node("div", "empty-state", emptyText));
			return;
		}
		items.forEach(function (source) {
			const itemData = mapper(source);
			const item = node("div", "list-item");
			const text = node("div");
			text.appendChild(node("strong", "", itemData.title));
			text.appendChild(node("p", "", itemData.detail || ""));
			item.appendChild(text);
			const controls = node("div", "actions actions-wrap");
			controls.appendChild(makeBadge(itemData.badge || "ok", itemData.tone || "muted"));
			(itemData.actions || []).forEach(function (action) {
				const button = node("button", "button button-secondary", action.label);
				button.type = "button";
				button.dataset.action = action.action;
				button.dataset.id = action.id;
				controls.appendChild(button);
			});
			item.appendChild(controls);
			box.appendChild(item);
		});
	}

	function renderTable(id, headers, rows, emptyText) {
		const box = clear(id);
		if (!rows || rows.length === 0) {
			box.appendChild(node("div", "empty-state", emptyText));
			return;
		}
		const table = node("table");
		const thead = node("thead");
		const headRow = node("tr");
		headers.forEach(function (header) {
			headRow.appendChild(node("th", "", header));
		});
		thead.appendChild(headRow);
		table.appendChild(thead);
		const tbody = node("tbody");
		rows.forEach(function (row) {
			const tr = node("tr");
			row.forEach(function (cell) {
				tr.appendChild(node("td", "", safeText(cell)));
			});
			tbody.appendChild(tr);
		});
		table.appendChild(tbody);
		box.appendChild(table);
	}

	function makeBadge(label, tone) {
		const badge = node("span", "badge badge-" + badgeTone(tone), safeText(label));
		return badge;
	}

	function setBadge(id, label, tone) {
		const target = byId(id);
		target.className = "badge badge-" + badgeTone(tone);
		target.textContent = safeText(label);
	}

	function badgeTone(tone) {
		if (tone === "ok") {
			return "ok";
		}
		if (tone === "error") {
			return "error";
		}
		if (tone === "pending") {
			return "pending";
		}
		return "muted";
	}

	function byId(id) {
		return document.getElementById(id);
	}

	function clear(id) {
		const element = byId(id);
		element.textContent = "";
		return element;
	}

	function setText(id, text) {
		byId(id).textContent = safeText(text);
	}

	function node(tag, className, text) {
		const element = document.createElement(tag);
		if (className) {
			element.className = className;
		}
		if (text !== undefined) {
			element.textContent = safeText(text);
		}
		return element;
	}

	function safeText(value) {
		if (value === null || value === undefined || value === "") {
			return "Sem dados";
		}
		const text = String(value);
		if (SENSITIVE_WORDS.some(function (word) {
			return text.includes(word);
		})) {
			return "[valor oculto]";
		}
		return text;
	}

	function dataOf(result) {
		return result && result.ok ? result.data : null;
	}

	function valueOf(result, key) {
		const data = dataOf(result);
		return data ? data[key] : undefined;
	}

	function arrayOf(result) {
		const data = dataOf(result);
		return Array.isArray(data) ? data : [];
	}

	function countOf(result) {
		return arrayOf(result).length;
	}

	function isOk(result) {
		return Boolean(result && result.ok);
	}

	function entriesFromLog(result) {
		const data = dataOf(result);
		if (!data) {
			return [];
		}
		if (Array.isArray(data.entries)) {
			return data.entries;
		}
		return Array.isArray(data) ? data : [];
	}

	function messageFor(result) {
		if (!result) {
			return "Nao carregado";
		}
		if (result.ok) {
			return "HTTP " + result.status;
		}
		return result.error || "Erro tratado";
	}

	function flag(value, yes, no) {
		return value ? yes : no;
	}

	function eventStateText(data) {
		if (!data) {
			return "Sem dados";
		}
		if (data.runnerRunning) {
			return "Ativo";
		}
		return "Parado";
	}

	function formatDate(value) {
		if (!value) {
			return "";
		}
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) {
			return "";
		}
		return date.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
	}

	function formatTime(date) {
		return date.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
	}

	function formatDuration(seconds) {
		if (!seconds && seconds !== 0) {
			return "Sem dados";
		}
		const mins = Math.floor(Number(seconds) / 60);
		if (mins < 1) {
			return String(seconds) + "s";
		}
		const hours = Math.floor(mins / 60);
		if (hours < 1) {
			return String(mins) + "min";
		}
		return String(hours) + "h " + String(mins % 60) + "min";
	}

	function setBusy(isBusy) {
		byId("refresh-dashboard").disabled = isBusy;
		document.querySelectorAll("[data-action]").forEach(function (button) {
			if (isBusy) {
				button.setAttribute("aria-busy", "true");
			}
			else {
				button.removeAttribute("aria-busy");
			}
		});
	}

	function toast(title, message, isError) {
		const region = byId("toast-region");
		const item = node("div", "toast" + (isError ? " error" : ""));
		item.appendChild(node("strong", "", title));
		item.appendChild(node("span", "", message));
		region.appendChild(item);
		window.setTimeout(function () {
			item.remove();
		}, 5200);
	}
})();
