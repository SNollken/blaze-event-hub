package com.blaze.eventhub.member;

import java.time.Instant;

public record Member(
		String id,
		String blazeUserId,
		String blazeUsername,
		String displayName,
		String avatarUrl,
		String status,
		Instant createdAt,
		Instant updatedAt) {
}
