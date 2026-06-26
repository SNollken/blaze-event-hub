package com.nollen.blaze.oauth;

public record OAuthProfileSyncResult(
		boolean attempted,
		boolean profilePresent,
		String status,
		OAuthProfileSummary profile) {

	public static OAuthProfileSyncResult skipped(OAuthProfileSummary profile) {
		return new OAuthProfileSyncResult(false, profile != null, "skipped", profile);
	}

	public static OAuthProfileSyncResult synced(OAuthProfileSummary profile) {
		return new OAuthProfileSyncResult(true, profile != null, profile == null ? "empty" : "synced", profile);
	}

	public static OAuthProfileSyncResult failed(OAuthProfileSummary profile) {
		return new OAuthProfileSyncResult(true, profile != null, "unavailable", profile);
	}
}
