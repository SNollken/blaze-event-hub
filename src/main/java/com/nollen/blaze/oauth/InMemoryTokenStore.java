package com.nollen.blaze.oauth;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Repository;

@Repository
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
