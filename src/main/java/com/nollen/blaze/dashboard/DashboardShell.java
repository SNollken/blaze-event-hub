package com.nollen.blaze.dashboard;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DashboardShell {

	private static final String HTML = """
			<!doctype html>
			<html lang="pt-BR">
			<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<title>NollenBlaze</title>
			</head>
			<body>
				<div id="root">NollenBlaze - Frontend React unificado</div>
				<script type="module" src="/src/main.tsx"></script>
			</body>
			</html>
			""";

	public ResponseEntity<String> response() {
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(HTML);
	}
}
