package com.blaze.eventhub.oauth;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory fallback token store. Only used when no JdbcTemplate is available
 * (e.g., tests without a database). In production, {@link JdbcTokenStore} is used.
 */
public class InMemoryTokenStore implements TokenStore {

	private final AtomicReference<TokenSnapshot> current = new AtomicReference<>();

	@Override
	public Optional<TokenSnapshot> current() {
		return Optional.ofNullable(current.get());
	}

	@Override
	public void save(TokenSnapshot tokenSnapshot) {
		current.set(tokenSnapshot);
	}

	@Override
	public void clear() {
		current.set(null);
	}
}
