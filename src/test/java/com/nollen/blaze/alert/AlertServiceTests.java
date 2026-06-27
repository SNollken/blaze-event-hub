package com.nollen.blaze.alert;

import java.util.List;
import java.util.Map;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;
import com.nollen.blaze.events.BlazeEventType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertServiceTests {

	private AlertService service;
	private AlertStore alertStore;
	private AlertRuleStore ruleStore;

	@BeforeEach
	void setUp() {
		alertStore = new AlertStore();
		ruleStore = new AlertRuleStore();
		service = new AlertService(alertStore, ruleStore, new AlertNotifier(), new IdGenerator());
	}

	@Test
	void acknowledgeAlert() {
		AlertRule rule = createRule("Rule1", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0);
		ruleStore.save(rule);
		List<Alert> triggered = service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));

		assertThat(triggered).hasSize(1);
		Alert acknowledged = service.acknowledge(triggered.getFirst().id());

		assertThat(acknowledged.acknowledged()).isTrue();
		assertThat(service.getActiveAlerts()).isEmpty();
	}

	@Test
	void acknowledgeNonExistentThrows() {
		assertThatThrownBy(() -> service.acknowledge("nonexistent"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void getActiveAlertsReturnsUnacknowledged() {
		AlertRule rule = createRule("Rule1", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0);
		ruleStore.save(rule);
		service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));
		service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));

		assertThat(service.getActiveAlerts()).hasSize(2);

		List<Alert> all = alertStore.findAll();
		service.acknowledge(all.getFirst().id());

		assertThat(service.getActiveAlerts()).hasSize(1);
	}

	@Test
	void alertHistoryFilterByEventType() {
		AlertRule followRule = createRule("Follow", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0);
		AlertRule subscribeRule = createRule("Sub", BlazeEventType.CHANNEL_SUBSCRIBE, AlertCondition.ALWAYS, 0);
		ruleStore.save(followRule);
		ruleStore.save(subscribeRule);

		service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));
		service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_SUBSCRIBE, Map.of()));

		List<Alert> followAlerts = service.getAlertHistory(BlazeEventType.CHANNEL_FOLLOW.id());
		assertThat(followAlerts).hasSize(1);
		assertThat(followAlerts.getFirst().eventType()).isEqualTo(BlazeEventType.CHANNEL_FOLLOW);

		List<Alert> all = service.getAlertHistory(null);
		assertThat(all).hasSize(2);
	}

	@Test
	void evaluateEventTriggersMatchingRules() {
		AlertRule rule = createRule("Big Gift", BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, AlertCondition.MIN_AMOUNT, 100.0);
		ruleStore.save(rule);

		List<Alert> noMatch = service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", 50.0)));
		assertThat(noMatch).isEmpty();

		List<Alert> match = service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", 200.0)));
		assertThat(match).hasSize(1);
		assertThat(match.getFirst().message()).contains("Big Gift");
	}

	@Test
	void getStatsReturnsCorrectCounts() {
		AlertRule enabledRule = createRule("Enabled", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0);
		AlertRule disabledRule = new AlertRule(new IdGenerator().newId(), "Disabled", BlazeEventType.CHANNEL_FOLLOW,
				AlertCondition.ALWAYS, 0, null, false, 0);
		ruleStore.save(enabledRule);
		ruleStore.save(disabledRule);

		service = new AlertService(alertStore, ruleStore, new AlertNotifier(), new IdGenerator());

		service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));

		AlertStatsResponse stats = service.getStats();
		assertThat(stats.totalRules()).isEqualTo(2);
		assertThat(stats.enabledRules()).isEqualTo(1);
		assertThat(stats.totalAlerts()).isEqualTo(1);
		assertThat(stats.unacknowledgedAlerts()).isEqualTo(1);
	}

	@Test
	void disabledRuleDoesNotTrigger() {
		AlertRule rule = createRule("DisabledRule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0);
		AlertRuleStore singleStore = new AlertRuleStore();
		singleStore.save(new AlertRule(rule.id(), rule.name(), rule.eventType(), rule.condition(),
				rule.threshold(), rule.template(), false, rule.cooldownMs()));
		service = new AlertService(alertStore, singleStore, new AlertNotifier(), new IdGenerator());

		List<Alert> result = service.evaluateEvent(new EvaluateEventRequest(BlazeEventType.CHANNEL_FOLLOW, Map.of()));
		assertThat(result).isEmpty();
	}

	private AlertRule createRule(String name, BlazeEventType type, AlertCondition condition, double threshold) {
		return new AlertRule(new IdGenerator().newId(), name, type, condition, threshold, null, true, 0);
	}
}
