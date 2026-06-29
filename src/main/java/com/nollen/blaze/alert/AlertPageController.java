package com.nollen.blaze.alert;

import com.nollen.blaze.dashboard.DashboardShell;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AlertPageController {

	private final DashboardShell shell;

	public AlertPageController(DashboardShell shell) {
		this.shell = shell;
	}

	@GetMapping("/alerts-dashboard")
	@ResponseBody
	ResponseEntity<String> dashboard() {
		return shell.response();
	}
}
