package com.blaze.eventhub.oauth;

import java.util.Map;

public interface OAuthProfileClient {

	Map<String, Object> getCurrentUserProfile();
}
