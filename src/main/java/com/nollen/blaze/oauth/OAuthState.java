package com.nollen.blaze.oauth;

import java.time.Instant;

/**
 * Pending OAuth authorization state.
 *
 * {@code sessionId} is the id of the HTTP session of the browser that called
 * {@code POST /api/blaze/oauth/start}. The callback is only accepted in the
 * same browser session — this blocks the account-fixation attack where an
 * attacker completes the flow with their own Blaze account and social-engineers
 * the finished callback URL onto the streamer (the token is stored globally,
 * so a successful cross-session callback would swap the connected account).
 */
public record OAuthState(
		String state,
		String codeVerifier,
		Instant createdAt,
		String sessionId) {
}
