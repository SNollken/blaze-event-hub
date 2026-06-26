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
		removeExpiredStates();
		states.put(state.state(), state);
	}

	@Override
	public Optional<OAuthState> find(String state) {
		if (state == null || state.isBlank()) {
			return Optional.empty();
		}
		OAuthState stored = states.get(state);
		if (stored == null) {
			return Optional.empty();
		}
		if (isExpired(stored)) {
			states.remove(state, stored);
			return Optional.empty();
		}
		return Optional.of(stored);
	}

	@Override
	public Optional<OAuthState> consume(String state) {
		if (state == null || state.isBlank()) {
			return Optional.empty();
		}
		OAuthState stored = states.remove(state);
		if (stored == null || isExpired(stored)) {
			return Optional.empty();
		}
		return Optional.of(stored);
	}

	@Override
	public void clear() {
		states.clear();
	}

	@Override
	public int size() {
		removeExpiredStates();
		return states.size();
	}

	private boolean isExpired(OAuthState state) {
		return state.createdAt().plus(STATE_TTL).isBefore(Instant.now(clock));
	}

	private void removeExpiredStates() {
		states.entrySet().removeIf(entry -> isExpired(entry.getValue()));
	}
}
