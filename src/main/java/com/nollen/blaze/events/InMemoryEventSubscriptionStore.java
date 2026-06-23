package com.nollen.blaze.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryEventSubscriptionStore {

	private final ConcurrentHashMap<String, EventSubscriptionSnapshot> subscriptions = new ConcurrentHashMap<>();

	public void save(EventSubscriptionSnapshot snapshot) {
		subscriptions.put(snapshot.id(), snapshot);
	}

	public List<EventSubscriptionSnapshot> list() {
		return new ArrayList<>(subscriptions.values());
	}

	public void clear() {
		subscriptions.clear();
	}
}
