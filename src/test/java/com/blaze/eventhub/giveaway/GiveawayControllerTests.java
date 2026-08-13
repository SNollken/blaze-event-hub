package com.blaze.eventhub.giveaway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"beh.blaze.client-id=",
		"beh.blaze.client-secret=",
})
class GiveawayControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GiveawayService giveawayService;

	@Autowired
	private InMemoryGiveawayStore giveawayStore;

	@Autowired
	private InMemoryGiveawayEntryStore entryStore;

	@BeforeEach
	void clearStores() {
		for (Giveaway giveaway : giveawayStore.findAll()) {
			entryStore.deleteByGiveawayId(giveaway.id());
			giveawayStore.delete(giveaway.id());
		}
	}

	@Test
	void entriesReturnsParticipantsInEntryOrder() throws Exception {
		Giveaway giveaway = giveawayService.createGiveaway(new CreateGiveawayRequest("Roleta", null, 10));
		giveawayService.openGiveaway(giveaway.id());
		giveawayService.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("alice"));
		giveawayService.enterGiveaway(giveaway.id(), new EnterGiveawayRequest("bob"));

		mockMvc.perform(get("/api/giveaways/{id}/entries", giveaway.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].participantName").value("alice"))
				.andExpect(jsonPath("$[1].participantName").value("bob"))
				.andExpect(jsonPath("$[0].id").isNotEmpty());
	}

	@Test
	void entriesReturnsEmptyListWhenNoParticipants() throws Exception {
		Giveaway giveaway = giveawayService.createGiveaway(new CreateGiveawayRequest("Sem participantes", null, 10));

		mockMvc.perform(get("/api/giveaways/{id}/entries", giveaway.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void entriesForUnknownGiveawayReturns404() throws Exception {
		mockMvc.perform(get("/api/giveaways/{id}/entries", "missing-giveaway"))
				.andExpect(status().isNotFound());
	}
}
