package com.blaze.eventhub.oauth;

import java.net.URI;

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
	private static final URI LOGIN_OAUTH_SUCCESS = URI.create("/login?oauth=success");
	private static final URI LOGIN_OAUTH_ERROR = URI.create("/login?oauth=error");

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
			return redirect(LOGIN_OAUTH_SUCCESS);
		}
		catch (OAuthException ex) {
			if (json) {
				throw ex;
			}
			return redirect(LOGIN_OAUTH_ERROR);
		}
		catch (RuntimeException ex) {
			if (json) {
				throw ex;
			}
			return redirect(LOGIN_OAUTH_ERROR);
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

	private static ResponseEntity<Void> redirect(URI location) {
		return ResponseEntity.status(HttpStatus.SEE_OTHER)
				.location(location)
				.build();
	}

}
