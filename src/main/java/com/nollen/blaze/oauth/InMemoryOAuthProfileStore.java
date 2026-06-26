package com.nollen.blaze.oauth;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Repository;

@Repository
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
