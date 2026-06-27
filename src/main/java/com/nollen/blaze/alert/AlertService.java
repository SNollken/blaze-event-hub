package com.nollen.blaze.alert;

import java.util.ArrayList;
import java.util.List;

import com.nollen.blaze.common.IdGenerator;

import org.springframework.stereotype.Service;

@Service
public class AlertService {

	private final AlertStore alertStore;
	private final AlertRuleStore ruleStore;
	private final AlertNotifier notifier;
	private final IdGenerator idGenerator;

	public AlertService(AlertStore alertStore, AlertRuleStore ruleStore, AlertNotifier notifier,
			IdGenerator idGenerator) {
		this.alertStore = alertStore;
		this.ruleStore = ruleStore;
		this.notifier = notifier;
		this.idGenerator = idGenerator;
	}

	public Alert acknowledge(String alertId) {
		Alert alert = alertStore.findById(alertId)
				.orElseThrow(() -> new com.nollen.blaze.common.NotFoundException("Alert not found: " + alertId));
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

	public List<Alert> evaluateEvent(EvaluateEventRequest request) {
		List<AlertRule> enabledRules = ruleStore.findAll().stream()
				.filter(AlertRule::enabled)
				.toList();
		List<Alert> triggered = new ArrayList<>();
		for (AlertRule rule : enabledRules) {
			if (AlertEvaluator.matches(rule, request.eventType(), request.payload())) {
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
				java.time.Instant.now(),
				message,
				false,
				request.payload() == null ? java.util.Map.of() : java.util.Map.copyOf(request.payload()));
	}
}
