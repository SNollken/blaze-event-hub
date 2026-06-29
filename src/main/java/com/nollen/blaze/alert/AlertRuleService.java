package com.nollen.blaze.alert;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.common.NotFoundException;

import org.springframework.stereotype.Service;

@Service
public class AlertRuleService {

	private final AlertRuleStore store;
	private final IdGenerator idGenerator;

	public AlertRuleService(AlertRuleStore store, IdGenerator idGenerator) {
		this.store = store;
		this.idGenerator = idGenerator;
	}

	public AlertRule create(CreateAlertRuleRequest request) {
		AlertRule rule = new AlertRule(
				idGenerator.newId(),
				request.name(),
				request.eventType(),
				request.condition(),
				request.threshold(),
				request.template(),
				request.enabled(),
				request.cooldownMs());
		return store.save(rule);
	}

	public AlertRule update(String id, UpdateAlertRuleRequest request) {
		AlertRule existing = requireRule(id);
		AlertRule updated = existing.withUpdated(
				request.name(),
				request.eventType(),
				request.condition(),
				request.threshold(),
				request.template(),
				request.enabled(),
				request.cooldownMs());
		return store.save(updated);
	}

	public void delete(String id) {
		requireRule(id);
		store.delete(id);
	}

	public Optional<AlertRule> findById(String id) {
		return store.findById(id);
	}

	public List<AlertRule> findAll() {
		return store.findAll();
	}

	public List<AlertRule> findEnabled() {
		return store.findAll().stream()
				.filter(AlertRule::enabled)
				.toList();
	}

	public List<AlertRuleSnapshot> toSnapshots(List<AlertRule> rules) {
		return rules.stream()
				.map(r -> new AlertRuleSnapshot(r.id(), r.name(), r.eventType(), r.condition(),
						r.threshold(), r.template(), r.enabled(), r.cooldownMs()))
				.collect(Collectors.toList());
	}

	private AlertRule requireRule(String id) {
		return store.findById(id)
				.orElseThrow(() -> new NotFoundException("Alert rule not found: " + id));
	}
}
