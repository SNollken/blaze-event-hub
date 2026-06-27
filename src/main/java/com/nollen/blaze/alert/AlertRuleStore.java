package com.nollen.blaze.alert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class AlertRuleStore {

	private final ConcurrentHashMap<String, AlertRule> rules = new ConcurrentHashMap<>();

	public AlertRule save(AlertRule rule) {
		rules.put(rule.id(), rule);
		return rule;
	}

	public Optional<AlertRule> findById(String id) {
		return Optional.ofNullable(rules.get(id));
	}

	public List<AlertRule> findAll() {
		return rules.values().stream()
				.sorted(Comparator.comparing(AlertRule::id))
				.toList();
	}

	public void delete(String id) {
		rules.remove(id);
	}

	public long count() {
		return rules.size();
	}
}
