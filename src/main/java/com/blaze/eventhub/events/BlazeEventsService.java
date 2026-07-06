package com.blaze.eventhub.events;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.blaze.eventhub.common.IdGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlazeEventsService {

	private final BlazeEventsRunner runner;
	private final BlazeEventsLogStore logStore;
	private final BlazeEventsCapabilities capabilities;
	private final IdGenerator idGenerator;
	private final Clock clock;
	private final BlazeEventsPipeline pipeline;

	public BlazeEventsService(BlazeEventsRunner runner, BlazeEventsLogStore logStore,
			BlazeEventsCapabilities capabilities, IdGenerator idGenerator, Clock clock) {
		this(runner, logStore, capabilities, idGenerator, clock, null);
	}

	@Autowired
	public BlazeEventsService(BlazeEventsRunner runner, BlazeEventsLogStore logStore,
			BlazeEventsCapabilities capabilities, IdGenerator idGenerator, Clock clock, BlazeEventsPipeline pipeline) {
		this.runner = runner;
		this.logStore = logStore;
		this.capabilities = capabilities;
		this.idGenerator = idGenerator;
		this.clock = clock;
		this.pipeline = pipeline;
	}

	public BlazeEventsStatusExtended status() {
		Instant lastReceived = null;
		List<BlazeEventsLogEntry> all = logStore.listAll();
		if (!all.isEmpty()) {
			lastReceived = all.get(all.size() - 1).timestamp();
		}
		return BlazeEventsStatusExtended.from(runner, lastReceived, logStore.count());
	}

	public BlazeEventsStatusResponse start() {
		runner.start();
		logEntry("system", "ENGINE_START", "Events engine started");
		return runner.status();
	}

	public BlazeEventsStatusResponse stop() {
		runner.stop();
		logEntry("system", "ENGINE_STOP", "Events engine stopped");
		return runner.status();
	}

	public void acceptEnvelope(BlazeEventEnvelope envelope) {
		runner.acceptEnvelope(envelope);
		if (pipeline == null && envelope != null) {
			logEntry(
					envelope.subscriptionType() != null ? envelope.subscriptionType() : "unknown",
					envelope.messageType() != null ? envelope.messageType() : "unknown",
					"Event received: " + (envelope.messageType() != null ? envelope.messageType() : "unknown"));
		}
	}

	public BlazeEventsLogEntry simulate(String eventType, String message) {
		if (pipeline != null) {
			return pipeline.simulate(eventType, message);
		}
		Instant now = Instant.now(clock);
		String resolvedType = eventType != null && !eventType.isBlank() ? eventType : "channel.chat.message";
		String resolvedMessage = message != null && !message.isBlank() ? message : "Simulated event: " + resolvedType;
		BlazeEventsLogEntry entry = new BlazeEventsLogEntry(
				idGenerator.newId(),
				now,
				resolvedType,
				"simulate",
				resolvedMessage,
				Map.of("simulated", true, "eventType", resolvedType, "timestamp", now.toString()).toString());
		logStore.append(entry);
		return entry;
	}

	public List<BlazeEventsLogEntry> getLog(String eventType, String source, int limit) {
		return logStore.list(eventType, source, limit);
	}

	public Map<String, Object> capabilities() {
		return capabilities.capabilities();
	}

	private void logEntry(String source, String eventType, String message) {
		BlazeEventsLogEntry entry = new BlazeEventsLogEntry(
				idGenerator.newId(),
				Instant.now(clock),
				eventType,
				source,
				message,
				null);
		logStore.append(entry);
	}

}
