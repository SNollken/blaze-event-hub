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

	/**
	 * Dev-only fallback served when the React build has not been bundled into
	 * the jar yet (run `cd frontend && npm run build` before packaging to get
	 * the real SPA). Production always ships the bundled SPA.
	 */
	private static final String FRONTEND_NOT_BUNDLED = """
			<!doctype html>
			<html lang="pt-BR">
			<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<title>Blaze Event Hub</title>
			</head>
			<body style="font-family: system-ui, sans-serif; background: #0B0D0E; color: #E8E5DE; display: grid; place-items: center; min-height: 100vh; margin: 0;">
				<main style="text-align: center; max-width: 420px; padding: 24px;">
					<h1 style="font-size: 22px; margin: 0 0 12px;">Blaze Event Hub</h1>
					<p style="color: #9CA0A6; margin: 0 0 16px;">O frontend ainda nao foi incluido neste build. Rode <code>cd frontend &amp;&amp; npm run build</code> e reempacote o jar.</p>
					<a href="/api/health" style="color: #FF6B4A;">Verificar API</a>
				</main>
			</body>
			</html>
			""";

	/**
	 * SPA entry for the deep-link routes. Serves the built React app when
	 * present (frontend/dist copied into the jar at package time); falls back
	 * to a placeholder page when the frontend has not been built.
	 */
	public ResponseEntity<String> response() {
		if (REACT_SPA.exists()) {
			return ResponseEntity.ok()
					.contentType(MediaType.TEXT_HTML)
					.body(load(REACT_SPA));
		}
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(FRONTEND_NOT_BUNDLED);
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
