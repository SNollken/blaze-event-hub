package com.nollen.blaze.intake;

import com.nollen.blaze.dashboard.DashboardShell;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LiveEventsPageController {

	private final DashboardShell shell;

	public LiveEventsPageController(DashboardShell shell) {
		this.shell = shell;
	}

	@GetMapping("/live-events")
	@ResponseBody
	ResponseEntity<String> liveEvents() {
		return shell.response();
	}
}
