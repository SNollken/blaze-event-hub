package com.blaze.eventhub.overlays.runtime;

import com.blaze.eventhub.dashboard.DashboardShell;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class OverlayPageController {

    private final DashboardShell shell;

    public OverlayPageController(DashboardShell shell) {
        this.shell = shell;
    }

    @GetMapping("/overlays-dashboard")
    @ResponseBody
    ResponseEntity<String> dashboard() {
        return shell.response();
    }
}
