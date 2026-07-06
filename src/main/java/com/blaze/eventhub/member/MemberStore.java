package com.blaze.eventhub.member;

import java.util.Optional;

public interface MemberStore {

	Member save(Member member);

	Optional<Member> findById(String id);

	Optional<Member> findByBlazeUserId(String blazeUserId);

	boolean existsByBlazeUserId(String blazeUserId);
}
