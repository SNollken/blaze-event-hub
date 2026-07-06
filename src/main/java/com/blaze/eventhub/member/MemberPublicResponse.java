package com.blaze.eventhub.member;

public record MemberPublicResponse(
		String id,
		String blazeUsername,
		String displayName,
		String avatarUrl,
		String status) {

	public static MemberPublicResponse from(Member member) {
		return new MemberPublicResponse(
				member.id(),
				member.blazeUsername(),
				member.displayName(),
				member.avatarUrl(),
				member.status());
	}
}
