package com.nollen.blaze.channel;

import java.util.List;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.springframework.stereotype.Service;

@Service
public class BlazeChannelService {

	private final BlazeChannelConfigStore store;
	private final IdGenerator idGenerator;

	public BlazeChannelService(BlazeChannelConfigStore store, IdGenerator idGenerator) {
		this.store = store;
		this.idGenerator = idGenerator;
	}

	public List<BlazeChannelConfig> list() {
		return store.list();
	}

	public BlazeChannelConfig findById(String id) {
		BlazeChannelConfig config = store.findById(id);
		if (config == null) {
			throw new NotFoundException("Channel config not found: " + id);
		}
		return config;
	}

	public BlazeChannelConfig create(CreateBlazeChannelRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Channel request is required");
		}
		String platform = request.platform();
		if (platform == null || platform.isBlank()) {
			platform = "blaze";
		}
		BlazeChannelConfig config = new BlazeChannelConfig(
				idGenerator.newId(),
				request.name(),
				request.channelId(),
				platform,
				request.monitored());
		store.save(config);
		return config;
	}

	public BlazeChannelConfig update(String id, CreateBlazeChannelRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Channel request is required");
		}
		BlazeChannelConfig existing = store.findById(id);
		if (existing == null) {
			throw new NotFoundException("Channel config not found: " + id);
		}
		String platform = request.platform();
		if (platform == null || platform.isBlank()) {
			platform = existing.platform();
		}
		BlazeChannelConfig updated = new BlazeChannelConfig(
				existing.id(),
				request.name(),
				request.channelId(),
				platform,
				request.monitored());
		store.save(updated);
		return updated;
	}

	public boolean delete(String id) {
		return store.deleteById(id);
	}
}
