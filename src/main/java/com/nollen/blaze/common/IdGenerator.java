package com.nollen.blaze.common;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

	private final SecureRandom random = new SecureRandom();

	public String newId() {
		return UUID.randomUUID().toString();
	}

	public String newPublicToken() {
		byte[] bytes = new byte[18];
		random.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
