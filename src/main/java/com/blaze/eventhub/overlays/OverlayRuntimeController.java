package com.blaze.eventhub.overlays;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.blaze.eventhub.common.NotFoundException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.PostConstruct;

@Controller
public class OverlayRuntimeController {

	private final ClassPathResource runtime = new ClassPathResource("static/overlay-runtime.html");
	private String cachedHtml;

	@PostConstruct
	void init() throws IOException {
		cachedHtml = runtime.getContentAsString(StandardCharsets.UTF_8);
	}

	@GetMapping("/overlay/{publicToken}")
	@ResponseBody
	ResponseEntity<String> runtime(@PathVariable String publicToken) {
		if (!StringUtils.hasText(publicToken) || publicToken.length() > 160) {
			throw new NotFoundException("Overlay runtime not found");
		}
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(cachedHtml);
	}
}
