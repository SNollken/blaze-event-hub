package com.nollen.blaze.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

	private final DashboardShell shell;

	public DashboardController(DashboardShell shell) {
		this.shell = shell;
	}

	@GetMapping({"/", "/dashboard", "/events", "/channel", "/alerts", "/giveaways", "/overlays"})
	@ResponseBody
	ResponseEntity<String> dashboard() {
		return shell.response();
	}
}
