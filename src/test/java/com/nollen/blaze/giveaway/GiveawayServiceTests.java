package com.nollen.blaze.giveaway;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.nollen.blaze.common.ConflictException;
import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GiveawayServiceTests {

	private GiveawayService service;

	@BeforeEach
	void setUp() {
		service = new GiveawayService(
				new InMemoryGiveawayStore(),
				new InMemoryGiveawayEntryStore(),
				new IdGenerator(),
				Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void createsDraftGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio Teste", "Descricao", 50));

		assertThat(giveaway.id()).isNotBlank();
		assertThat(giveaway.title()).isEqualTo("Sorteio Teste");
		assertThat(giveaway.description()).isEqualTo("Descricao");
		assertThat(giveaway.status()).isEqualTo(GiveawayStatus.DRAFT);
		assertThat(giveaway.entryCount()).isEqualTo(0);
		assertThat(giveaway.maxEntries()).isEqualTo(50);
	}

	@Test
	void createsGiveawayWithDefaultMaxEntries() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, null));

		assertThat(giveaway.maxEntries()).isEqualTo(1000);
		assertThat(giveaway.description()).isEmpty();
	}

	@Test
	void openGiveawayTransitionsFromDraft() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, null));
		Giveaway opened = service.openGiveaway(giveaway.id());

		assertThat(opened.status()).isEqualTo(GiveawayStatus.OPEN);
		assertThat(opened.openedAt()).isNotNull();
	}

	@Test
	void cannotOpenNonDraftGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, null));
		service.openGiveaway(giveaway.id());

		assertThatThrownBy(() -> service.openGiveaway(giveaway.id()))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("DRAFT");
	}

	@Test
	void closeGiveawayTransitionsFromOpen() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, null));
		service.openGiveaway(giveaway.id());
		Giveaway closed = service.closeGiveaway(giveaway.id());

		assertThat(closed.status()).isEqualTo(GiveawayStatus.CLOSED);
		assertThat(closed.closedAt()).isNotNull();
	}

	@Test
	void enterGiveawayAddsEntry() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		GiveawayEntry entry = service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));

		assertThat(entry.participantName()).isEqualTo("Joao");
		assertThat(entry.giveawayId()).isEqualTo(giveaway.id());
		assertThat(entry.eligible()).isTrue();
		assertThat(entry.selected()).isFalse();

		Giveaway updated = service.getGiveaway(giveaway.id());
		assertThat(updated.entryCount()).isEqualTo(1);
	}

	@Test
	void cannotEnterClosedGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.closeGiveaway(giveaway.id());

		assertThatThrownBy(() -> service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("not open");
	}

	@Test
	void cannotEnterSameParticipantTwice() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));

		assertThatThrownBy(() -> service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("already entered");
	}

	@Test
	void cannotExceedMaxEntries() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 2));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Maria"));

		assertThatThrownBy(() -> service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Pedro")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("maximum entries");
	}

	@Test
	void drawWinnersSelectsParticipants() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Maria"));
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Pedro"));
		service.closeGiveaway(giveaway.id());

		Giveaway completed = service.drawWinners(giveaway.id(), 1);

		assertThat(completed.status()).isEqualTo(GiveawayStatus.COMPLETED);
		assertThat(completed.winnerIds()).hasSize(1);
		assertThat(completed.drawnAt()).isNotNull();
	}

	@Test
	void drawMultipleWinners() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Maria"));
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Pedro"));
		service.closeGiveaway(giveaway.id());

		Giveaway completed = service.drawWinners(giveaway.id(), 2);

		assertThat(completed.winnerIds()).hasSize(2);
	}

	@Test
	void drawMoreWinnersThanEntriesReturnsAll() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));
		service.closeGiveaway(giveaway.id());

		Giveaway completed = service.drawWinners(giveaway.id(), 5);

		assertThat(completed.winnerIds()).hasSize(1);
	}

	@Test
	void cannotDrawFromEmptyGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.closeGiveaway(giveaway.id());

		assertThatThrownBy(() -> service.drawWinners(giveaway.id(), 1))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("No entries");
	}

	@Test
	void cannotDrawWhenNotClosed() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));

		assertThatThrownBy(() -> service.drawWinners(giveaway.id(), 1))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("CLOSED");
	}

	@Test
	void getResultsReturnsCorrectData() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, 10));
		service.openGiveaway(giveaway.id());
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Joao"));
		service.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("Maria"));
		service.closeGiveaway(giveaway.id());
		service.drawWinners(giveaway.id(), 1);

		GiveawayResultsResponse results = service.getResults(giveaway.id());

		assertThat(results.giveawayId()).isEqualTo(giveaway.id());
		assertThat(results.totalEntries()).isEqualTo(2);
		assertThat(results.winnerCount()).isEqualTo(1);
		assertThat(results.winners()).hasSize(1);
		assertThat(results.drawnAt()).isNotNull();
	}

	@Test
	void updateOnlyDraftGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Original", null, null));
		service.openGiveaway(giveaway.id());

		assertThatThrownBy(() -> service.updateGiveaway(giveaway.id(), new UpdateGiveawayRequest("Novo", null)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("DRAFT");
	}

	@Test
	void updateGiveawayTitle() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Original", null, null));
		Giveaway updated = service.updateGiveaway(giveaway.id(), new UpdateGiveawayRequest("Atualizado", "Nova desc"));

		assertThat(updated.title()).isEqualTo("Atualizado");
		assertThat(updated.description()).isEqualTo("Nova desc");
	}

	@Test
	void deleteDraftGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, null));
		service.deleteGiveaway(giveaway.id());

		assertThatThrownBy(() -> service.getGiveaway(giveaway.id()))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void cannotDeleteOpenGiveaway() {
		Giveaway giveaway = service.createGiveaway(new CreateGiveawayRequest("Sorteio", null, null));
		service.openGiveaway(giveaway.id());

		assertThatThrownBy(() -> service.deleteGiveaway(giveaway.id()))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("DRAFT or CANCELLED");
	}

	@Test
	void getStatsReturnsCorrectCounts() {
		service.createGiveaway(new CreateGiveawayRequest("A", null, null));
		Giveaway b = service.createGiveaway(new CreateGiveawayRequest("B", null, null));
		service.openGiveaway(b.id());

		GiveawayStatsResponse stats = service.getStats();

		assertThat(stats.totalGiveaways()).isEqualTo(2);
		assertThat(stats.draftCount()).isEqualTo(1);
		assertThat(stats.openCount()).isEqualTo(1);
		assertThat(stats.totalEntries()).isEqualTo(0);
	}

	@Test
	void getGiveawayNotFound() {
		assertThatThrownBy(() -> service.getGiveaway("nonexistent"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void fullLifecycle() {
		// Create -> Open -> Enter -> Close -> Draw -> Results
		Giveaway gw = service.createGiveaway(new CreateGiveawayRequest("Lifecycle Test", "Test desc", 100));
		assertThat(gw.status()).isEqualTo(GiveawayStatus.DRAFT);

		Giveaway opened = service.openGiveaway(gw.id());
		assertThat(opened.status()).isEqualTo(GiveawayStatus.OPEN);

		service.enterGiveaway(gw.id(), new EnterGiveawayRequest("Ana"));
		service.enterGiveaway(gw.id(), new EnterGiveawayRequest("Bruno"));
		service.enterGiveaway(gw.id(), new EnterGiveawayRequest("Carlos"));
		assertThat(service.getGiveaway(gw.id()).entryCount()).isEqualTo(3);

		Giveaway closed = service.closeGiveaway(gw.id());
		assertThat(closed.status()).isEqualTo(GiveawayStatus.CLOSED);

		Giveaway completed = service.drawWinners(gw.id(), 2);
		assertThat(completed.status()).isEqualTo(GiveawayStatus.COMPLETED);
		assertThat(completed.winnerIds()).hasSize(2);

		GiveawayResultsResponse results = service.getResults(gw.id());
		assertThat(results.totalEntries()).isEqualTo(3);
		assertThat(results.winnerCount()).isEqualTo(2);
		assertThat(results.winners()).hasSize(2);
	}
}
