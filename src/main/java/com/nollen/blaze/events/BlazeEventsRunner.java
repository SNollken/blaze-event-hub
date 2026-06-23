package com.nollen.blaze.events;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class BlazeEventsRunner {

	private static final String SESSION_WELCOME = "session_welcome";

	private final BlazeEventsClient client;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicReference<String> sessionId = new AtomicReference<>();
	private final AtomicReference<String> lastMessageType = new AtomicReference<>();
	private volatile Instant startedAt;

	public BlazeEventsRunner(BlazeEventsClient client, Clock clock) {
		this.client = client;
		this.clock = clock;
	}

	public void start() {
		if (running.compareAndSet(false, true)) {
			client.start();
			startedAt = Instant.now(clock);
		}
	}

	public void stop() {
		if (running.compareAndSet(true, false)) {
			client.stop();
			sessionId.set(null);
			startedAt = null;
		}
	}

	public void acceptEnvelope(BlazeEventEnvelope envelope) {
		if (envelope == null) {
			return;
		}
		lastMessageType.set(envelope.messageType());
		if (SESSION_WELCOME.equals(envelope.messageType()) && envelope.sessionId() != null) {
			sessionId.set(envelope.sessionId());
		}
	}

	public String currentSessionId() {
		return sessionId.get();
	}

	public BlazeEventsStatusResponse status() {
		return new BlazeEventsStatusResponse(
				running.get(),
				client.isRunning(),
				sessionId.get(),
				lastMessageType.get(),
				startedAt);
	}
}
