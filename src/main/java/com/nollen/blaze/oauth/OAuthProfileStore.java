package com.nollen.blaze.oauth;

import java.util.Optional;

public interface OAuthProfileStore {

	Optional<OAuthProfileSummary> current();

	void save(OAuthProfileSummary profile);

	void clear();
}
