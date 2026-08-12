package com.blaze.eventhub.giveaway;

import com.blaze.eventhub.dashboard.DashboardShell;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class GiveawayDashboardController {

	private final DashboardShell shell;

	public GiveawayDashboardController(DashboardShell shell) {
		this.shell = shell;
	}

	@GetMapping("/giveaways-dashboard")
	@ResponseBody
	ResponseEntity<String> dashboard() {
		return shell.response();
	}
}
