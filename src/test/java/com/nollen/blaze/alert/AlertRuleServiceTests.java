package com.nollen.blaze.alert;

import java.util.List;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;
import com.nollen.blaze.events.BlazeEventType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleServiceTests {

	private AlertRuleService service;
	private AlertRuleStore store;

	@BeforeEach
	void setUp() {
		store = new AlertRuleStore();
		service = new AlertRuleService(store, new IdGenerator());
	}

	@Test
	void createAlertRule() {
		CreateAlertRuleRequest request = new CreateAlertRuleRequest(
				"Test Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0);

		AlertRule rule = service.create(request);

		assertThat(rule.id()).isNotBlank();
		assertThat(rule.name()).isEqualTo("Test Rule");
		assertThat(rule.eventType()).isEqualTo(BlazeEventType.CHANNEL_FOLLOW);
		assertThat(rule.enabled()).isTrue();
	}

	@Test
	void updateAlertRule() {
		CreateAlertRuleRequest createReq = new CreateAlertRuleRequest(
				"Original", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0);
		AlertRule created = service.create(createReq);

		UpdateAlertRuleRequest updateReq = new UpdateAlertRuleRequest(
				"Updated", BlazeEventType.CHANNEL_SUBSCRIBE, AlertCondition.MIN_AMOUNT, 50.0, "template", false, 3000);
		AlertRule updated = service.update(created.id(), updateReq);

		assertThat(updated.name()).isEqualTo("Updated");
		assertThat(updated.eventType()).isEqualTo(BlazeEventType.CHANNEL_SUBSCRIBE);
		assertThat(updated.condition()).isEqualTo(AlertCondition.MIN_AMOUNT);
		assertThat(updated.threshold()).isEqualTo(50.0);
		assertThat(updated.enabled()).isFalse();
		assertThat(updated.cooldownMs()).isEqualTo(3000);
	}

	@Test
	void deleteAlertRule() {
		CreateAlertRuleRequest request = new CreateAlertRuleRequest(
				"Delete Me", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0);
		AlertRule created = service.create(request);

		service.delete(created.id());

		assertThat(service.findById(created.id())).isEmpty();
	}

	@Test
	void updateNonExistentRuleThrows() {
		UpdateAlertRuleRequest request = new UpdateAlertRuleRequest(
				"X", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0);

		assertThatThrownBy(() -> service.update("nonexistent", request))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void findEnabledReturnsOnlyEnabled() {
		service.create(new CreateAlertRuleRequest("Enabled", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, true, 0));
		service.create(new CreateAlertRuleRequest("Disabled", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, null, false, 0));

		List<AlertRule> enabled = service.findEnabled();

		assertThat(enabled).hasSize(1);
		assertThat(enabled.getFirst().name()).isEqualTo("Enabled");
	}

	@Test
	void toSnapshotsConvertsCorrectly() {
		service.create(new CreateAlertRuleRequest("Snap Rule", BlazeEventType.CHANNEL_FOLLOW, AlertCondition.ALWAYS, 0, "tpl", true, 0));

		List<AlertRuleSnapshot> snapshots = service.toSnapshots(service.findAll());

		assertThat(snapshots).hasSize(1);
		assertThat(snapshots.getFirst().name()).isEqualTo("Snap Rule");
		assertThat(snapshots.getFirst().template()).isEqualTo("tpl");
	}
}
