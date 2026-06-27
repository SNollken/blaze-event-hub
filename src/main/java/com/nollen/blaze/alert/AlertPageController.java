package com.nollen.blaze.alert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AlertPageController {

	private final ClassPathResource dashboard = new ClassPathResource("static/alerts-dashboard.html");

	@GetMapping("/alerts-dashboard")
	ResponseEntity<String> dashboard() throws IOException {
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(StreamUtils.copyToString(dashboard.getInputStream(), StandardCharsets.UTF_8));
	}
}
