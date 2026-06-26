package com.nollen.blaze.oauth;

import java.util.Map;

public interface OAuthProfileClient {

	Map<String, Object> getCurrentUserProfile();
}
