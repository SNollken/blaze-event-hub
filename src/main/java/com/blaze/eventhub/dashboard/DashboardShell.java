package com.blaze.eventhub.dashboard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class DashboardShell {

	private static final ClassPathResource REACT_SPA = new ClassPathResource("static/index.html");

	private static final ClassPathResource MVP_PANEL = new ClassPathResource("static/dashboard.html");

	/**
	 * SPA entry for the deep-link routes. Serves the built React app when
	 * present (frontend/dist copied into the jar at package time); falls back
	 * to the MVP panel when the frontend has not been built.
	 */
	public ResponseEntity<String> response() {
		ClassPathResource entry = REACT_SPA.exists() ? REACT_SPA : MVP_PANEL;
		return html(entry);
	}

	/** Legacy MVP panel, kept available at /dashboard and /dashboard.html. */
	public ResponseEntity<String> mvpPanel() {
		return html(MVP_PANEL);
	}

	private ResponseEntity<String> html(ClassPathResource resource) {
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(load(resource));
	}

	private String load(ClassPathResource resource) {
		try (InputStream in = resource.getInputStream()) {
			return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Dashboard shell file not found: " + resource.getPath(), ex);
		}
	}
}
