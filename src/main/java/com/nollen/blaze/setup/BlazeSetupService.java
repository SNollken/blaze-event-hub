package com.nollen.blaze.setup;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.nollen.blaze.config.BlazeProperties;
import com.nollen.blaze.events.BlazeEventsRunner;
import com.nollen.blaze.events.BlazeEventsStatusResponse;
import com.nollen.blaze.oauth.TokenSnapshot;
import com.nollen.blaze.oauth.TokenStore;

import org.springframework.stereotype.Service;

@Service
public class BlazeSetupService {

	private static final String APP_NAME = "NollenBlaze";
	private static final List<BlazeSetupScopeResponse> RECOMMENDED_SCOPES = List.of(
			new BlazeSetupScopeResponse("users.read", "MVP_3", false,
					"Confirmar perfil do usuario depois do OAuth."),
			new BlazeSetupScopeResponse("offline.access", "MVP_3", false,
					"Permitir renovacao segura do token de usuario pelo backend."),
			new BlazeSetupScopeResponse("channel.moderate", "FUTURO_CHAT_MODERACAO", false,
					"Usar somente quando chat/moderacao forem realmente habilitados."),
			new BlazeSetupScopeResponse("users.bot", "FUTURO_CHAT_BOT", false,
					"Usar somente quando uma conta de usuario for autorizada como bot."));
	private static final List<BlazeSetupDocsLinkResponse> DOCS_LINKS = List.of(
			new BlazeSetupDocsLinkResponse("App Setup", "https://dev.blaze.stream/docs/app-setup"),
			new BlazeSetupDocsLinkResponse("OAuth", "https://dev.blaze.stream/docs/oauth"),
			new BlazeSetupDocsLinkResponse("Scopes", "https://dev.blaze.stream/docs/scopes"),
			new BlazeSetupDocsLinkResponse("Events", "https://dev.blaze.stream/docs/events"));

	private final BlazeProperties properties;
	private final TokenStore tokenStore;
	private final BlazeEventsRunner eventsRunner;
	private final Clock clock;

	public BlazeSetupService(BlazeProperties properties, TokenStore tokenStore, BlazeEventsRunner eventsRunner, Clock clock) {
		this.properties = properties;
		this.tokenStore = tokenStore;
		this.eventsRunner = eventsRunner;
		this.clock = clock;
	}

	public BlazeSetupStatusResponse currentStatus() {
		TokenSnapshot token = tokenStore.current().orElse(null);
		boolean clientIdConfigured = hasText(properties.getClientId());
		boolean clientCredentialConfigured = hasText(properties.getClientSecret());
		boolean redirectUriConfigured = hasText(properties.getRedirectUri());
		boolean tokenPresent = token != null && !token.accessTokenBlank();
		boolean refreshCredentialPresent = token != null && !token.refreshTokenBlank();
		boolean monitoredChannelConfigured = properties.isMonitoredChannelConfigured();
		BlazeEventsStatusResponse eventsStatus = eventsRunner.status();
		boolean eventsConfigReady = properties.isSocketConfigured() && monitoredChannelConfigured && tokenPresent;
		boolean oauthStartReady = clientIdConfigured && clientCredentialConfigured && redirectUriConfigured;

		List<BlazeSetupItemResponse> checklist = new ArrayList<>();
		addItem(checklist, "client_id", "Client ID", clientIdConfigured,
				"Copie o Client ID da Blaze Developer Console para BLAZE_CLIENT_ID.");
		addItem(checklist, "client_credential", "Client Secret", clientCredentialConfigured,
				"Guarde o valor somente no backend local em BLAZE_CLIENT_SECRET.");
		addItem(checklist, "redirect_uri", "Redirect URI", redirectUriConfigured,
				"Cadastre esta URL exatamente na Blaze Developer Console.");
		addItem(checklist, "scopes", "Scopes", !properties.getScopes().isEmpty(),
				"Comece com users.read,offline.access e adie scopes de chat/moderacao.");
		addItem(checklist, "token", "Token de usuario", tokenPresent,
				"Inicie OAuth somente depois de preencher a configuracao local.");
		addItem(checklist, "refresh_credential", "Credencial de renovacao", refreshCredentialPresent,
				"Ela deve existir apenas depois do OAuth e nunca aparecer no frontend.");
		addItem(checklist, "monitored_channel", "Canal monitorado", monitoredChannelConfigured,
				"Preencha BLAZE_MONITORED_CHANNEL_ID quando for testar canal real.");
		addItem(checklist, "events", "Events", eventsConfigReady,
				"Events real fica para MVP futuro; hoje o setup mostra se os pre-requisitos existem.");

		List<BlazeSetupItemResponse> missingItems = checklist.stream()
				.filter(item -> "missing".equals(item.status()))
				.toList();

		return new BlazeSetupStatusResponse(
				APP_NAME,
				environment(properties.getRedirectUri()),
				clientIdConfigured,
				clientIdConfigured ? mask(properties.getClientId()) : null,
				clientCredentialConfigured,
				redirectUriConfigured,
				properties.getRedirectUri(),
				properties.getScopes(),
				RECOMMENDED_SCOPES,
				tokenPresent,
				tokenExpiredOrUnknown(token),
				refreshCredentialPresent,
				monitoredChannelConfigured,
				monitoredChannelConfigured ? mask(properties.getMonitoredChannelId()) : null,
				eventsConfigReady,
				oauthStartReady,
				checklist,
				missingItems,
				nextSteps(clientIdConfigured, clientCredentialConfigured, redirectUriConfigured, tokenPresent,
						monitoredChannelConfigured, eventsStatus),
				DOCS_LINKS,
				envExample(properties.getRedirectUri()));
	}

	private static void addItem(List<BlazeSetupItemResponse> items, String code, String label, boolean ok, String help) {
		items.add(new BlazeSetupItemResponse(code, label, ok ? "ok" : "missing", help));
	}

	private List<String> nextSteps(boolean clientIdConfigured, boolean clientCredentialConfigured,
			boolean redirectUriConfigured, boolean tokenPresent, boolean monitoredChannelConfigured,
			BlazeEventsStatusResponse eventsStatus) {
		List<String> steps = new ArrayList<>();
		if (!clientIdConfigured || !clientCredentialConfigured) {
			steps.add("Crie ou abra o app na Blaze Developer Console e copie Client ID e Client Secret para o .env local.");
		}
		if (!redirectUriConfigured) {
			steps.add("Configure BLAZE_REDIRECT_URI e cadastre a mesma URL na Blaze Developer Console.");
		}
		steps.add("Use os scopes minimos users.read,offline.access para o proximo MVP de OAuth/perfil.");
		if (!tokenPresent) {
			steps.add("Reinicie o backend depois do .env e clique em Iniciar OAuth.");
		}
		if (!monitoredChannelConfigured) {
			steps.add("Preencha BLAZE_MONITORED_CHANNEL_ID antes de testar canal e Events.");
		}
		if (eventsStatus.sessionId() == null || eventsStatus.sessionId().isBlank()) {
			steps.add("Deixe Events real para o MVP futuro depois de validar OAuth e canal.");
		}
		return List.copyOf(steps);
	}

	private boolean tokenExpiredOrUnknown(TokenSnapshot token) {
		if (token == null || token.accessTokenBlank()) {
			return true;
		}
		return token.expiresAt() == null || token.expiresAt().isBefore(Instant.now(clock));
	}

	private static String environment(String redirectUri) {
		return redirectUri != null && redirectUri.contains("localhost") ? "local" : "configured";
	}

	private static String envExample(String redirectUri) {
		String safeRedirectUri = hasText(redirectUri) ? redirectUri : "http://localhost:8080/api/blaze/oauth/callback";
		return String.join("\n",
				"BLAZE_CLIENT_ID=<cole_o_client_id_aqui>",
				"BLAZE_CLIENT_SECRET=<cole_a_credencial_do_app_somente_no_backend>",
				"BLAZE_REDIRECT_URI=" + safeRedirectUri,
				"BLAZE_SCOPES=users.read,offline.access",
				"BLAZE_MONITORED_CHANNEL_ID=<opcional_uuid_do_canal>");
	}

	private static String mask(String value) {
		if (!hasText(value)) {
			return null;
		}
		if (value.length() <= 8) {
			return "***";
		}
		return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
