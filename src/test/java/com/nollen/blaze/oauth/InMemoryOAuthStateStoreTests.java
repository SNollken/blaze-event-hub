package com.nollen.blaze.oauth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOAuthStateStoreTests {

	private MutableClock clock;
	private InMemoryOAuthStateStore store;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-06-23T12:00:00Z"));
		store = new InMemoryOAuthStateStore(clock);
	}

	@Test
	void savesStateAndCodeVerifier() {
		store.save(new OAuthState("state-1", "verifier-1", clock.instant()));

		assertThat(store.find("state-1")).isPresent();
		assertThat(store.find("state-1").orElseThrow().codeVerifier()).isEqualTo("verifier-1");
	}

	@Test
	void consumeRemovesOnlyTheRequestedState() {
		store.save(new OAuthState("state-1", "verifier-1", clock.instant()));
		store.save(new OAuthState("state-2", "verifier-2", clock.instant()));

		assertThat(store.consume("state-1")).isPresent();

		assertThat(store.find("state-1")).isEmpty();
		assertThat(store.find("state-2")).isPresent();
		assertThat(store.size()).isEqualTo(1);
	}

	@Test
	void supportsMultiplePendingStates() {
		store.save(new OAuthState("state-1", "verifier-1", clock.instant()));
		store.save(new OAuthState("state-2", "verifier-2", clock.instant()));

		assertThat(store.find("state-1")).isPresent();
		assertThat(store.find("state-2")).isPresent();
		assertThat(store.size()).isEqualTo(2);
	}

	@Test
	void doesNotExpireBeforeTtl() {
		store.save(new OAuthState("state-1", "verifier-1", clock.instant()));

		clock.advanceSeconds(599);

		assertThat(store.find("state-1")).isPresent();
	}

	@Test
	void expiresAfterTtl() {
		store.save(new OAuthState("state-1", "verifier-1", clock.instant()));

		clock.advanceSeconds(601);

		assertThat(store.find("state-1")).isEmpty();
		assertThat(store.size()).isZero();
	}

	@Test
	void consumeDoesNotReturnExpiredState() {
		store.save(new OAuthState("state-1", "verifier-1", clock.instant()));

		clock.advanceSeconds(601);

		assertThat(store.consume("state-1")).isEmpty();
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advanceSeconds(long seconds) {
			instant = instant.plusSeconds(seconds);
		}
	}
}
