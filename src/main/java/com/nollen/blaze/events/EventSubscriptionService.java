package com.nollen.blaze.events;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.nollen.blaze.common.ConfigurationMissingException;
import com.nollen.blaze.common.IdGenerator;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EventSubscriptionService {

	private final BlazeEventsRunner runner;
	private final InMemoryEventSubscriptionStore store;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public EventSubscriptionService(BlazeEventsRunner runner, InMemoryEventSubscriptionStore store, IdGenerator idGenerator, Clock clock) {
		this.runner = runner;
		this.store = store;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public List<EventSubscriptionSnapshot> sync(EventSubscriptionRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Blaze Events subscription request is required");
		}
		if (request.type() == null) {
			throw new IllegalArgumentException("Blaze Events subscription type is required");
		}
		if (!StringUtils.hasText(request.channelId())) {
			throw new IllegalArgumentException("Blaze Events channelId is required");
		}
		String sessionId = runner.currentSessionId();
		if (sessionId == null || sessionId.isBlank()) {
			throw new ConfigurationMissingException("Blaze Events session is not available yet");
		}
		EventSubscriptionSnapshot snapshot = new EventSubscriptionSnapshot(
				idGenerator.newId(),
				request.type(),
				request.effectiveVersion(),
				request.channelId(),
				sessionId,
				Instant.now(clock));
		store.save(snapshot);
		return store.list();
	}

	public List<EventSubscriptionSnapshot> list() {
		return store.list();
	}
}
