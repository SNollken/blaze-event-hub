package com.nollen.blaze.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BlazeEventsLogStoreTests {

	private final BlazeEventsLogStore store = new BlazeEventsLogStore();

	@Test
	void appendAndList() {
		BlazeEventsLogEntry entry = new BlazeEventsLogEntry("id1", Instant.now(), "chat", "simulate", "test", null);
		store.append(entry);

		assertThat(store.list(null, null, 10)).hasSize(1);
		assertThat(store.count()).isEqualTo(1);
	}

	@Test
	void listFiltersByEventType() {
		store.append(new BlazeEventsLogEntry("1", Instant.now(), "chat", "simulate", "msg1", null));
		store.append(new BlazeEventsLogEntry("2", Instant.now(), "follow", "simulate", "msg2", null));

		assertThat(store.list("chat", null, 10)).hasSize(1);
		assertThat(store.list("follow", null, 10)).hasSize(1);
	}

	@Test
	void listFiltersBySource() {
		store.append(new BlazeEventsLogEntry("1", Instant.now(), "chat", "simulate", "msg1", null));
		store.append(new BlazeEventsLogEntry("2", Instant.now(), "chat", "system", "msg2", null));

		assertThat(store.list(null, "simulate", 10)).hasSize(1);
	}

	@Test
	void listRespectsLimit() {
		for (int i = 0; i < 10; i++) {
			store.append(new BlazeEventsLogEntry("id" + i, Instant.now(), "chat", "simulate", "msg", null));
		}
		assertThat(store.list(null, null, 3)).hasSize(3);
	}

	@Test
	void listCapsLimitAtMaxQueryLimit() {
		// even a huge limit cannot fetch more than MAX_QUERY_LIMIT rows
		for (int i = 0; i < 10; i++) {
			store.append(new BlazeEventsLogEntry("cap-" + i, Instant.now(), "chat", "simulate", "msg", null));
		}
		assertThat(store.list(null, null, Integer.MAX_VALUE).size())
				.isLessThanOrEqualTo(BlazeEventsLogStore.MAX_QUERY_LIMIT);
	}

	@Test
	void clearRemovesAll() {
		store.append(new BlazeEventsLogEntry("1", Instant.now(), "chat", "simulate", "msg", null));
		store.clear();
		assertThat(store.count()).isEqualTo(0);
	}

	@Test
	void findLastTimestampReturnsLatest() {
		store.append(new BlazeEventsLogEntry("1", Instant.ofEpochSecond(100), "chat", "simulate", "msg1", null));
		store.append(new BlazeEventsLogEntry("2", Instant.ofEpochSecond(200), "chat", "simulate", "msg2", null));
		assertThat(store.findLastTimestamp()).isEqualTo(Instant.ofEpochSecond(200));
	}

	@Test
	void findLastTimestampReturnsNullWhenEmpty() {
		assertThat(store.findLastTimestamp()).isNull();
	}
}
