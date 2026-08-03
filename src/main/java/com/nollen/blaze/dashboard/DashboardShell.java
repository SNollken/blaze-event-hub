package com.nollen.blaze.dashboard;

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

	private final ClassPathResource dashboard = new ClassPathResource("static/dashboard.html");

	public ResponseEntity<String> response() {
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(loadDashboard());
	}

	private String loadDashboard() {
		try (InputStream in = dashboard.getInputStream()) {
			return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Dashboard shell file not found", ex);
		}
	}
}
