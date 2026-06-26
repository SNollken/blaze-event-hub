package com.nollen.blaze.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthProfileService {

	private static final List<String> PROFILE_CONTAINERS = List.of("data", "user", "profile", "me");

	private final OAuthProfileClient profileClient;
	private final OAuthProfileStore profileStore;
	private final Clock clock;

	public OAuthProfileService(OAuthProfileClient profileClient, OAuthProfileStore profileStore, Clock clock) {
		this.profileClient = profileClient;
		this.profileStore = profileStore;
		this.clock = clock;
	}

	public OAuthProfileSyncResult synchronizeCurrentUser() {
		try {
			Map<String, Object> rawProfile = profileClient.getCurrentUserProfile();
			OAuthProfileSummary profile = sanitize(rawProfile);
			if (profile != null) {
				profileStore.save(profile);
			}
			return OAuthProfileSyncResult.synced(profile);
		}
		catch (Exception ex) {
			return OAuthProfileSyncResult.failed(profileStore.current().orElse(null));
		}
	}

	public OAuthProfileSyncResult currentResult() {
		return OAuthProfileSyncResult.skipped(profileStore.current().orElse(null));
	}

	public void clear() {
		profileStore.clear();
	}

	private OAuthProfileSummary sanitize(Map<String, Object> rawProfile) {
		if (rawProfile == null || rawProfile.isEmpty()) {
			return null;
		}
		String id = firstText(rawProfile, "id", "userId", "user_id");
		String username = firstText(rawProfile, "username", "login", "slug");
		String displayName = firstText(rawProfile, "displayName", "display_name", "name", "nickname");
		String avatarUrl = firstText(rawProfile, "avatarUrl", "avatar_url", "profileImageUrl", "profile_image_url", "picture");
		if (!StringUtils.hasText(id) && !StringUtils.hasText(username) && !StringUtils.hasText(displayName)) {
			return null;
		}
		return new OAuthProfileSummary(
				clean(id, 120),
				clean(username, 120),
				clean(displayName, 160),
				cleanUrl(avatarUrl),
				Instant.now(clock));
	}

	private static String firstText(Map<String, Object> rawProfile, String... keys) {
		String direct = firstDirectText(rawProfile, keys);
		if (StringUtils.hasText(direct)) {
			return direct;
		}
		for (String container : PROFILE_CONTAINERS) {
			Object nested = rawProfile.get(container);
			if (nested instanceof Map<?, ?> nestedMap) {
				String nestedText = firstDirectText(nestedMap, keys);
				if (StringUtils.hasText(nestedText)) {
					return nestedText;
				}
			}
		}
		return null;
	}

	private static String firstDirectText(Map<?, ?> source, String... keys) {
		for (String key : keys) {
			Object value = source.get(key);
			if (value instanceof String text && StringUtils.hasText(text)) {
				return text;
			}
			if (value instanceof Number || value instanceof Boolean) {
				return String.valueOf(value);
			}
		}
		return null;
	}

	private static String clean(String value, int maxLength) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String clean = value.trim();
		return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
	}

	private static String cleanUrl(String value) {
		String clean = clean(value, 500);
		if (!StringUtils.hasText(clean)) {
			return null;
		}
		return clean.startsWith("https://") || clean.startsWith("http://") ? clean : null;
	}
}
