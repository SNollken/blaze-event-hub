package com.nollen.blaze.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class BlazeChannelConfigStore {

	private final ConcurrentHashMap<String, BlazeChannelConfig> channels = new ConcurrentHashMap<>();

	public void save(BlazeChannelConfig config) {
		channels.put(config.id(), config);
	}

	public BlazeChannelConfig findById(String id) {
		return channels.get(id);
	}

	public List<BlazeChannelConfig> list() {
		return new ArrayList<>(channels.values());
	}

	public boolean deleteById(String id) {
		return channels.remove(id) != null;
	}

	public long count() {
		return channels.size();
	}
}
