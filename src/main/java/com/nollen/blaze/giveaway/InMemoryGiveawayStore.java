package com.nollen.blaze.giveaway;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGiveawayStore {

	private final ConcurrentHashMap<String, Giveaway> giveaways = new ConcurrentHashMap<>();

	public Giveaway save(Giveaway giveaway) {
		giveaways.put(giveaway.id(), giveaway);
		return giveaway;
	}

	public Optional<Giveaway> findById(String id) {
		return Optional.ofNullable(giveaways.get(id));
	}

	public List<Giveaway> findAll() {
		return giveaways.values().stream()
				.sorted(Comparator.comparing(Giveaway::createdAt))
				.toList();
	}

	public void delete(String id) {
		giveaways.remove(id);
	}

	public int count() {
		return giveaways.size();
	}
}
