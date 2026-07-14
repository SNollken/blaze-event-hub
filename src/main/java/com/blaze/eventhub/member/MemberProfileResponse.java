package com.blaze.eventhub.member;

import java.time.Instant;

public record MemberProfileResponse(
		String id,
		String blazeUserId,
		String blazeUsername,
		String displayName,
		String avatarUrl,
		String status,
		Instant createdAt,
		Instant updatedAt) {

	public static MemberProfileResponse from(Member member) {
		return new MemberProfileResponse(
				member.id(),
				member.blazeUserId(),
				member.blazeUsername(),
				member.displayName(),
				member.avatarUrl(),
				member.status(),
				member.createdAt(),
				member.updatedAt());
	}
}
