package com.blaze.eventhub.events;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class NoopBlazeEventsClient implements BlazeEventsClient {

	private final AtomicBoolean running = new AtomicBoolean(false);

	@Override
	public void start() {
		running.set(true);
	}

	@Override
	public void stop() {
		running.set(false);
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}
}
