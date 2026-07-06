package com.blaze.eventhub.oauth;

import java.util.Optional;

public interface TokenStore {

	Optional<TokenSnapshot> current();

	void save(TokenSnapshot tokenSnapshot);

	void clear();
}
