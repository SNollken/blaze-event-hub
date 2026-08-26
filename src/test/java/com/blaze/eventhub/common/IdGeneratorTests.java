package com.blaze.eventhub.common;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class IdGeneratorTests {

	private final IdGenerator generator = new IdGenerator();

	@Test
	void newIdReturnsValidUniqueUuids() {
		String id = generator.newId();
		assertThatCode(() -> UUID.fromString(id)).doesNotThrowAnyException();

		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			ids.add(generator.newId());
		}
		assertThat(ids).hasSize(1000);
	}

	@Test
	void newPublicTokenIs36CharLowerHex() {
		String token = generator.newPublicToken();
		// 18 bytes aleatorios -> 36 caracteres hex
		assertThat(token).hasSize(36);
		assertThat(token).matches("[0-9a-f]{36}");
	}

	@Test
	void newPublicTokenIsUnique() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			tokens.add(generator.newPublicToken());
		}
		assertThat(tokens).hasSize(1000);
	}
}
