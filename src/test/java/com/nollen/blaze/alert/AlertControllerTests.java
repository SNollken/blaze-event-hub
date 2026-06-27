package com.nollen.blaze.alert;

import java.util.Map;

import com.nollen.blaze.events.BlazeEventType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"nollen.blaze.client-id=",
		"nollen.blaze.client-secret=",
})
class AlertControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AlertRuleStore alertRuleStore;

	@Autowired
	private AlertStore alertStore;

	@BeforeEach
	void clearStores() {
		alertRuleStore.findAll().forEach(r -> alertRuleStore.delete(r.id()));
	}

	@Test
	void listRulesEmptyInitially() throws Exception {
		mockMvc.perform(get("/api/alerts/rules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void createAndRetrieveAlertRule() throws Exception {
		String createJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Test Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, "template", true, 0));

		mockMvc.perform(post("/api/alerts/rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Test Rule"))
				.andExpect(jsonPath("$.enabled").value(true));

		mockMvc.perform(get("/api/alerts/rules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Test Rule"));
	}

	@Test
	void updateAlertRule() throws Exception {
		String createJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Original", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0));

		String responseBody = mockMvc.perform(post("/api/alerts/rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String ruleId = objectMapper.readTree(responseBody).get("id").asText();

		String updateJson = objectMapper.writeValueAsString(new UpdateAlertRuleRequest(
				"Updated Name", BlazeEventType.CHANNEL_SUBSCRIBE, AlertCondition.MIN_AMOUNT, 50.0, "tpl", false, 3000));

		mockMvc.perform(put("/api/alerts/rules/{id}", ruleId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Updated Name"))
				.andExpect(jsonPath("$.condition").value("MIN_AMOUNT"))
				.andExpect(jsonPath("$.enabled").value(false));
	}

	@Test
	void deleteAlertRule() throws Exception {
		String createJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Delete Me", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0));

		String responseBody = mockMvc.perform(post("/api/alerts/rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String ruleId = objectMapper.readTree(responseBody).get("id").asText();

		mockMvc.perform(delete("/api/alerts/rules/{id}", ruleId))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/alerts/rules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void activeAlertsEmptyInitially() throws Exception {
		mockMvc.perform(get("/api/alerts/active"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void evaluateCreatesAlerts() throws Exception {
		String createJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Eval Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0));

		mockMvc.perform(post("/api/alerts/rules")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
				.andExpect(status().isOk());

		String evalJson = objectMapper.writeValueAsString(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));

		mockMvc.perform(post("/api/alerts/evaluate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(evalJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].ruleName").value("Eval Rule"))
				.andExpect(jsonPath("$[0].acknowledged").value(false));
	}

	@Test
	void acknowledgeAlert() throws Exception {
		String createJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Ack Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0));

		mockMvc.perform(post("/api/alerts/rules")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
				.andExpect(status().isOk());

		String evalJson = objectMapper.writeValueAsString(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));
		String evalResponse = mockMvc.perform(post("/api/alerts/evaluate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(evalJson))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String alertId = objectMapper.readTree(evalResponse).get(0).get("id").asText();

		mockMvc.perform(post("/api/alerts/acknowledge/{id}", alertId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acknowledged").value(true));

		mockMvc.perform(get("/api/alerts/active"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void statsEndpoint() throws Exception {
		mockMvc.perform(get("/api/alerts/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRules").isNumber())
				.andExpect(jsonPath("$.enabledRules").isNumber())
				.andExpect(jsonPath("$.totalAlerts").isNumber())
				.andExpect(jsonPath("$.unacknowledgedAlerts").isNumber())
				.andExpect(jsonPath("$.rules").isArray());
	}

	@Test
	void capabilitiesEndpoint() throws Exception {
		mockMvc.perform(get("/api/alerts/capabilities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventTypes").isArray())
				.andExpect(jsonPath("$.conditions").isArray())
				.andExpect(jsonPath("$.maxAlerts").value(1000));
	}

	@Test
	void historyEndpoint() throws Exception {
		mockMvc.perform(get("/api/alerts/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	void alertsDashboardPageServes() throws Exception {
		mockMvc.perform(get("/alerts-dashboard"))
				.andExpect(status().isOk());
	}

	@Test
	void filterRulesByEventType() throws Exception {
		String followJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Follow Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0));
		String subJson = objectMapper.writeValueAsString(new CreateAlertRuleRequest(
				"Sub Rule", BlazeEventType.CHANNEL_SUBSCRIBE, AlertCondition.ALWAYS, 0, null, true, 0));

		mockMvc.perform(post("/api/alerts/rules").contentType(MediaType.APPLICATION_JSON).content(followJson));
		mockMvc.perform(post("/api/alerts/rules").contentType(MediaType.APPLICATION_JSON).content(subJson));

		mockMvc.perform(get("/api/alerts/rules").param("eventType", "channel.follow"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Follow Rule"));
	}
}
