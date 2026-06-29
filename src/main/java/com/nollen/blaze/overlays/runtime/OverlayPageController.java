package com.nollen.blaze.overlays.runtime;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.util.StreamUtils;

@Controller
public class OverlayPageController {

    private final ClassPathResource dashboard = new ClassPathResource("static/overlays-dashboard.html");

    @GetMapping("/overlays-dashboard")
    ResponseEntity<String> dashboard() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(StreamUtils.copyToString(dashboard.getInputStream(), StandardCharsets.UTF_8));
    }
}
