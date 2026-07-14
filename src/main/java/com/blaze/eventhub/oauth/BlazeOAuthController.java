package com.blaze.eventhub.oauth;

import com.blaze.eventhub.common.OAuthException;
import com.blaze.eventhub.config.SessionCookiePolicy;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/blaze/oauth")
public class BlazeOAuthController {

	private final BlazeOAuthService oAuthService;
	private final SessionCookiePolicy sessionCookiePolicy;

	public BlazeOAuthController(BlazeOAuthService oAuthService, SessionCookiePolicy sessionCookiePolicy) {
		this.oAuthService = oAuthService;
		this.sessionCookiePolicy = sessionCookiePolicy;
	}

	@GetMapping("/session")
	OAuthSessionResponse session() {
		return oAuthService.session();
	}

	@GetMapping("/start")
	OAuthStartResponse start() {
		return oAuthService.start();
	}

	@GetMapping("/callback")
	ResponseEntity<?> callback(@RequestParam(value = "code", required = false) String code,
			@RequestParam(value = "state", required = false) String state,
			@RequestParam(value = "error", required = false) String error,
			@RequestParam(value = "error_description", required = false) String errorDescription,
			@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
			HttpServletRequest request) {
		boolean json = acceptsJson(accept);
		try {
			OAuthCallbackResponse response = oAuthService.callback(code, state, error, errorDescription);
			request.changeSessionId();
			if (json) {
				return ResponseEntity.ok(response);
			}
			return html(HttpStatus.OK, successPage(response));
		}
		catch (OAuthException ex) {
			if (json) {
				throw ex;
			}
			HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
			return html(status == null ? HttpStatus.BAD_REQUEST : status, errorPage("Nao foi possivel conectar a Blaze."));
		}
		catch (RuntimeException ex) {
			if (json) {
				throw ex;
			}
			return html(HttpStatus.BAD_GATEWAY, errorPage("Nao foi possivel concluir a conexao com a Blaze."));
		}
	}

	@PostMapping("/refresh")
	OAuthActionResponse refresh() {
		return oAuthService.refresh();
	}

	@PostMapping("/disconnect")
	ResponseEntity<OAuthActionResponse> disconnect(HttpServletRequest request) {
		OAuthActionResponse body = oAuthService.disconnect();
		var session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, sessionCookiePolicy.expiredCookie().toString())
				.body(body);
	}

	private static boolean acceptsJson(String accept) {
		return accept != null && accept.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
	}

	private static ResponseEntity<String> html(HttpStatus status, String body) {
		return ResponseEntity.status(status)
				.contentType(MediaType.TEXT_HTML)
				.body(body);
	}

	private static String successPage(OAuthCallbackResponse response) {
		String profileStatus = response.profilePresent()
				? "Perfil Blaze sincronizado."
				: "Conta conectada. Perfil ainda indisponivel; atualize a sessao pelo dashboard.";
		return page("Blaze conectada com sucesso", profileStatus, true);
	}

	private static String errorPage(String message) {
		return page("Falha ao conectar Blaze", message + " Volte ao dashboard e tente novamente.", false);
	}

	private static String page(String title, String message, boolean success) {
		String tone = success ? "#067647" : "#b42318";
		String returnUrl = success ? "/login?oauth=success" : "/login?oauth=error";
		return """
				<!doctype html>
				<html lang="pt-BR">
				<head>
					<meta charset="utf-8">
					<meta name="viewport" content="width=device-width, initial-scale=1">
					<title>%s</title>
					<style>
						body { margin: 0; min-height: 100vh; display: grid; place-items: center; font-family: Arial, Helvetica, sans-serif; background: #f5f7fb; color: #152033; }
						main { width: min(560px, calc(100%% - 32px)); background: #fff; border: 1px solid #d8e0eb; border-radius: 8px; padding: 28px; box-shadow: 0 1px 2px rgba(15, 23, 42, .06); }
						.status { margin: 0 0 10px; color: %s; font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0; }
						h1 { margin: 0 0 12px; font-size: 28px; line-height: 1.15; }
						p { margin: 0 0 20px; color: #475569; line-height: 1.5; }
						a { display: inline-block; border: 1px solid #2f5f99; border-radius: 6px; background: #2f5f99; color: #fff; font-weight: 700; padding: 10px 14px; text-decoration: none; }
					</style>
				</head>
				<body>
					<main>
						<p class="status">Blaze Event Hub</p>
						<h1>%s</h1>
						<p>%s</p>
						<a href="%s">Voltar ao Blaze Event Hub</a>
					</main>
				</body>
				</html>
				""".formatted(title, tone, title, message, returnUrl);
	}
}
