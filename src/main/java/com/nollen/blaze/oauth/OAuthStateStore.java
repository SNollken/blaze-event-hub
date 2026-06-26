package com.nollen.blaze.oauth;

import java.util.Optional;

public interface OAuthStateStore {

	void save(OAuthState state);

	Optional<OAuthState> find(String state);

	Optional<OAuthState> consume(String state);

	int size();
}
