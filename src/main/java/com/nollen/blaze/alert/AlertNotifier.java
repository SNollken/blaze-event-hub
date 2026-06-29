package com.nollen.blaze.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

	public void notify(Alert alert) {
		log.info("ALERT TRIGGERED: rule='{}' type='{}' message='{}' id='{}'",
				alert.ruleName(),
				alert.eventType(),
				alert.message(),
				alert.id());
	}
}
