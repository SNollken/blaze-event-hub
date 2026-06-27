package com.nollen.blaze.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

@Repository
public class BlazeEventsLogStore {

	private static final int MAX_ENTRIES = 500;

	private final ConcurrentLinkedDeque<BlazeEventsLogEntry> entries = new ConcurrentLinkedDeque<>();

	public void append(BlazeEventsLogEntry entry) {
		entries.addLast(entry);
		while (entries.size() > MAX_ENTRIES) {
			entries.pollFirst();
		}
	}

	public List<BlazeEventsLogEntry> list(String eventType, String source, int limit) {
		return entries.stream()
				.filter(e -> eventType == null || eventType.isBlank() || eventType.equalsIgnoreCase(e.eventType()))
				.filter(e -> source == null || source.isBlank() || source.equalsIgnoreCase(e.source()))
				.sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
				.limit(limit > 0 ? limit : 50)
				.collect(Collectors.toList());
	}

	public List<BlazeEventsLogEntry> listAll() {
		return new ArrayList<>(entries);
	}

	public void clear() {
		entries.clear();
	}

	public long count() {
		return entries.size();
	}
}
