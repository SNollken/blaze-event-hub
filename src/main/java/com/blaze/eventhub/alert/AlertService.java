package com.blaze.eventhub.alert;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.blaze.eventhub.common.IdGenerator;

import org.springframework.stereotype.Service;

@Service
public class AlertService {

	private final Clock clock;
	private final AlertStore alertStore;
	private final AlertRuleStore ruleStore;
	private final AlertNotifier notifier;
	private final IdGenerator idGenerator;

	public AlertService(Clock clock, AlertStore alertStore, AlertRuleStore ruleStore, AlertNotifier notifier,
			IdGenerator idGenerator) {
		this.clock = clock;
		this.alertStore = alertStore;
		this.ruleStore = ruleStore;
		this.notifier = notifier;
		this.idGenerator = idGenerator;
	}

	public Alert acknowledge(String alertId) {
		Alert alert = alertStore.findById(alertId)
				.orElseThrow(() -> new com.blaze.eventhub.common.NotFoundException("Alert not found: " + alertId));
		Alert acknowledged = alert.acknowledge();
		alertStore.save(acknowledged);
		return acknowledged;
	}

	public List<Alert> getActiveAlerts() {
		return alertStore.findActive();
	}

	public List<Alert> getAlertHistory(String eventTypeId) {
		if (eventTypeId != null && !eventTypeId.isBlank()) {
			return alertStore.findByEventType(eventTypeId);
		}
		return alertStore.findAll();
	}

	public AlertStatsResponse getStats() {
		List<AlertRule> allRules = ruleStore.findAll();
		long enabledCount = allRules.stream().filter(AlertRule::enabled).count();
		List<AlertRuleSnapshot> snapshots = allRules.stream()
				.map(r -> new AlertRuleSnapshot(r.id(), r.name(), r.eventType(), r.condition(),
						r.threshold(), r.template(), r.enabled(), r.cooldownMs()))
				.toList();
		return new AlertStatsResponse(
				allRules.size(),
				enabledCount,
				alertStore.count(),
				alertStore.countUnacknowledged(),
				alertStore.count() - alertStore.countUnacknowledged(),
				snapshots);
	}

	// ponytail: synchronized so the cooldown check-then-save cannot interleave between
	// concurrent calls (REST /api/alerts/evaluate vs event pipeline dispatch). Coarse
	// (whole method) like GiveawayService.enterGiveaway; switch to per-rule locks if
	// alert volume grows and contention becomes measurable.
	public synchronized List<Alert> evaluateEvent(EvaluateEventRequest request) {
		List<AlertRule> enabledRules = ruleStore.findAll().stream()
				.filter(AlertRule::enabled)
				.toList();
		List<String> cooldownRuleIds = enabledRules.stream()
				.filter(r -> r.cooldownMs() > 0)
				.map(AlertRule::id)
				.toList();
		Map<String, Optional<Alert>> lastByRule = cooldownRuleIds.isEmpty()
				? Map.of()
				: alertStore.findLastByRuleIds(cooldownRuleIds);
		List<Alert> triggered = new ArrayList<>();
		for (AlertRule rule : enabledRules) {
			if (AlertEvaluator.matches(rule, request.eventType(), request.payload())) {
				if (rule.cooldownMs() > 0) {
					Optional<Alert> lastTriggered = lastByRule.getOrDefault(rule.id(), Optional.empty());
					if (lastTriggered.isPresent()
							&& clock.instant().isBefore(lastTriggered.get().triggeredAt().plusMillis(rule.cooldownMs()))) {
						continue;
					}
				}
				Alert alert = createAlert(rule, request);
				alertStore.save(alert);
				notifier.notify(alert);
				triggered.add(alert);
			}
		}
		return triggered;
	}

	private Alert createAlert(AlertRule rule, EvaluateEventRequest request) {
		String message = AlertEvaluator.buildMessage(rule, request);
		return new Alert(
				idGenerator.newId(),
				rule.id(),
				rule.name(),
				request.eventType(),
				clock.instant(),
				message,
				false,
				request.payload() == null ? Map.of() : Map.copyOf(request.payload()));
	}
}
