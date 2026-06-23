package com.nollen.blaze.oauth;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOAuthStateStore implements OAuthStateStore {

	private static final Duration STATE_TTL = Duration.ofMinutes(10);

	private final ConcurrentHashMap<String, OAuthState> states = new ConcurrentHashMap<>();
	private final Clock clock;

	public InMemoryOAuthStateStore() {
		this(Clock.systemUTC());
	}

	InMemoryOAuthStateStore(Clock clock) {
		this.clock = clock;
	}

	@Override
	public void save(OAuthState state) {
		states.put(state.state(), state);
	}

	@Override
	public Optional<OAuthState> consume(String state) {
		if (state == null || state.isBlank()) {
			return Optional.empty();
		}
		OAuthState stored = states.remove(state);
		if (stored == null || stored.createdAt().plus(STATE_TTL).isBefore(Instant.now(clock))) {
			return Optional.empty();
		}
		return Optional.of(stored);
	}

	@Override
	public int size() {
		return states.size();
	}
}
