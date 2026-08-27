package com.blaze.eventhub.oauth;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory fallback profile store. Only used when no JdbcTemplate is available
 * (e.g., tests without a database). In production, {@link JdbcOAuthProfileStore} is used.
 */
public class InMemoryOAuthProfileStore implements OAuthProfileStore {

	private final AtomicReference<OAuthProfileSummary> current = new AtomicReference<>();

	@Override
	public Optional<OAuthProfileSummary> current() {
		return Optional.ofNullable(current.get());
	}

	@Override
	public void save(OAuthProfileSummary profile) {
		current.set(profile);
	}

	@Override
	public void clear() {
		current.set(null);
	}
}
