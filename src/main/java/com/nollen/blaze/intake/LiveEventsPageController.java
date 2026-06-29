package com.nollen.blaze.intake;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LiveEventsPageController {

	private final ClassPathResource page = new ClassPathResource("static/live-events.html");

	@GetMapping("/live-events")
	ResponseEntity<String> liveEvents() throws IOException {
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(StreamUtils.copyToString(page.getInputStream(), StandardCharsets.UTF_8));
	}
}
