package com.nollen.blaze.channel;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlazeChannelServiceTests {

	private final IdGenerator idGenerator = new IdGenerator();
	private final BlazeChannelConfigStore store = new BlazeChannelConfigStore();
	private final BlazeChannelService service = new BlazeChannelService(store, idGenerator);

	@Test
	void createChannelConfig() {
		CreateBlazeChannelRequest request = new CreateBlazeChannelRequest("Nollen", "ch-123", "blaze", true);
		BlazeChannelConfig config = service.create(request);

		assertThat(config.id()).isNotBlank();
		assertThat(config.name()).isEqualTo("Nollen");
		assertThat(config.channelId()).isEqualTo("ch-123");
		assertThat(config.platform()).isEqualTo("blaze");
		assertThat(config.monitored()).isTrue();
	}

	@Test
	void createChannelDefaultPlatform() {
		CreateBlazeChannelRequest request = new CreateBlazeChannelRequest("Test", "ch-456", null, false);
		BlazeChannelConfig config = service.create(request);

		assertThat(config.platform()).isEqualTo("blaze");
	}

	@Test
	void listChannels() {
		assertThat(service.list()).isEmpty();
		service.create(new CreateBlazeChannelRequest("A", "c1", "blaze", true));
		service.create(new CreateBlazeChannelRequest("B", "c2", "blaze", false));
		assertThat(service.list()).hasSize(2);
	}

	@Test
	void findChannelById() {
		BlazeChannelConfig created = service.create(new CreateBlazeChannelRequest("X", "cx", "blaze", true));
		BlazeChannelConfig found = service.findById(created.id());
		assertThat(found.name()).isEqualTo("X");
	}

	@Test
	void findByIdNotFound() {
		assertThatThrownBy(() -> service.findById("nonexistent"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void updateChannel() {
		BlazeChannelConfig created = service.create(new CreateBlazeChannelRequest("Old", "c-old", "blaze", false));
		BlazeChannelConfig updated = service.update(created.id(),
				new CreateBlazeChannelRequest("New", "c-new", "blaze", true));

		assertThat(updated.id()).isEqualTo(created.id());
		assertThat(updated.name()).isEqualTo("New");
		assertThat(updated.channelId()).isEqualTo("c-new");
		assertThat(updated.monitored()).isTrue();
	}

	@Test
	void updateChannelNotFound() {
		assertThatThrownBy(() -> service.update("nonexistent",
				new CreateBlazeChannelRequest("X", "c", "blaze", false)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void deleteChannel() {
		BlazeChannelConfig created = service.create(new CreateBlazeChannelRequest("D", "cd", "blaze", false));
		assertThat(service.delete(created.id())).isTrue();
		assertThat(service.list()).isEmpty();
	}

	@Test
	void deleteChannelNotFound() {
		assertThat(service.delete("nonexistent")).isFalse();
	}
}
