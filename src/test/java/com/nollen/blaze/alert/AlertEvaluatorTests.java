package com.nollen.blaze.alert;

import java.util.Map;

import com.nollen.blaze.events.BlazeEventType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEvaluatorTests {

	@Test
	void alwaysConditionMatchesAnyEvent() {
		AlertRule rule = new AlertRule("r1", "Always Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0);

		boolean matches = AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_FOLLOW, Map.of());

		assertThat(matches).isTrue();
	}

	@Test
	void alwaysConditionMatchesOnlyEventType() {
		AlertRule rule = new AlertRule("r1", "Follow Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0);

		boolean matches = AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIBE, Map.of());

		assertThat(matches).isFalse();
	}

	@Test
	void minAmountConditionMatchesWhenAboveThreshold() {
		AlertRule rule = new AlertRule("r1", "Gift Rule", BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, AlertCondition.MIN_AMOUNT, 100.0, null, true, 0);

		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", 150.0))).isTrue();
		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", 50.0))).isFalse();
		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", 100.0))).isTrue();
	}

	@Test
	void minAmountConditionHandlesStringValues() {
		AlertRule rule = new AlertRule("r1", "Gift Rule", BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, AlertCondition.MIN_AMOUNT, 100.0, null, true, 0);

		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", "200"))).isTrue();
		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of("amount", "50"))).isFalse();
	}

	@Test
	void minAmountConditionWithNullPayload() {
		AlertRule rule = new AlertRule("r1", "Gift Rule", BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, AlertCondition.MIN_AMOUNT, 100.0, null, true, 0);

		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, null)).isFalse();
	}

	@Test
	void minAmountConditionWithMissingAmount() {
		AlertRule rule = new AlertRule("r1", "Gift Rule", BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, AlertCondition.MIN_AMOUNT, 100.0, null, true, 0);

		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of())).isFalse();
	}

	@Test
	void raidMinSizeConditionMatches() {
		AlertRule rule = new AlertRule("r1", "Raid Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.RAID_MIN_SIZE, 10.0, null, true, 0);

		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_FOLLOW, Map.of("size", 15))).isTrue();
		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_FOLLOW, Map.of("size", 5))).isFalse();
	}

	@Test
	void raidMinSizeWithNullPayload() {
		AlertRule rule = new AlertRule("r1", "Raid Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.RAID_MIN_SIZE, 10.0, null, true, 0);

		assertThat(AlertEvaluator.matches(rule, BlazeEventType.CHANNEL_FOLLOW, null)).isFalse();
	}

	@Test
	void buildMessageIncludesRuleDetails() {
		AlertRule rule = new AlertRule("r1", "Big Gift", BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, AlertCondition.MIN_AMOUNT, 100.0, null, true, 0);
		EvaluateEventRequest request = new EvaluateEventRequest(BlazeEventType.CHANNEL_SUBSCRIPTION_GIFT, Map.of());

		String message = AlertEvaluator.buildMessage(rule, request);

		assertThat(message).contains("Big Gift");
		assertThat(message).contains("MIN_AMOUNT");
		assertThat(message).contains("100.0");
	}
}
