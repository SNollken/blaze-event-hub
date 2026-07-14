package com.blaze.eventhub.oauth;

import java.util.Optional;

public interface OAuthCredentialStore {

    void save(String memberId, TokenSnapshot token);

    Optional<TokenSnapshot> findByMemberId(String memberId);

    void deleteByMemberId(String memberId);

    void deleteByBlazeUserId(String blazeUserId);
}
