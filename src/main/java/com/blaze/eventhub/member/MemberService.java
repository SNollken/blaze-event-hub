package com.blaze.eventhub.member;

import java.time.Clock;
import java.time.Instant;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.oauth.TokenStore;

import org.springframework.stereotype.Service;

@Service
public class MemberService {

	private final MemberStore memberStore;
	private final TokenStore tokenStore;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public MemberService(MemberStore memberStore, TokenStore tokenStore, IdGenerator idGenerator, Clock clock) {
		this.memberStore = memberStore;
		this.tokenStore = tokenStore;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public Member createOrUpdateFromOAuth(String blazeUserId, String username, String displayName,
			String avatarUrl, String walletAddress, String accessToken, String refreshToken) {
		Instant now = Instant.now(clock);
		var existing = memberStore.findByBlazeUserId(blazeUserId);
		if (existing.isPresent()) {
			Member current = existing.get();
			Member updated = new Member(
					current.id(),
					current.blazeUserId(),
					username != null ? username : current.blazeUsername(),
					displayName != null ? displayName : current.displayName(),
					avatarUrl != null ? avatarUrl : current.avatarUrl(),
					walletAddress != null ? walletAddress : current.walletAddress(),
					accessToken != null ? accessToken : current.accessTokenEncrypted(),
					refreshToken != null ? refreshToken : current.refreshTokenEncrypted(),
					current.status(),
					current.createdAt(),
					now);
			return memberStore.save(updated);
		}
		Member newMember = new Member(
				idGenerator.newId(),
				blazeUserId,
				username != null ? username : blazeUserId,
				displayName != null ? displayName : (username != null ? username : blazeUserId),
				avatarUrl,
				walletAddress,
				accessToken,
				refreshToken,
				"active",
				now,
				now);
		return memberStore.save(newMember);
	}

	public Member getCurrentMember() {
		TokenSnapshot token = tokenStore.current()
				.orElseThrow(() -> new NotFoundException("Nenhuma sessao Blaze ativa. Conecte sua conta Blaze primeiro."));
		String blazeUserId = token.userId();
		if (blazeUserId == null || blazeUserId.isBlank()) {
			throw new NotFoundException("Sessao Blaze sem identificacao de usuario. Reconecte sua conta.");
		}
		return memberStore.findByBlazeUserId(blazeUserId)
				.orElseThrow(() -> new NotFoundException("Membro nao encontrado para o usuario Blaze " + blazeUserId));
	}

	public Member findById(String id) {
		return memberStore.findById(id)
				.orElseThrow(() -> new NotFoundException("Membro nao encontrado: " + id));
	}

	public Member findByBlazeUserId(String blazeUserId) {
		return memberStore.findByBlazeUserId(blazeUserId)
				.orElseThrow(() -> new NotFoundException("Membro nao encontrado para blazeUserId: " + blazeUserId));
	}
}
