package com.nollen.blaze.overlays.runtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nollen.blaze.common.NotFoundException;

@RestController
@RequestMapping("/api/overlay-runtimes/sse")
public class OverlaySseController {

	private final RuntimeOverlayConfigService configService;
	private final OverlayContentService contentService;
	private final ObjectMapper objectMapper;

	public OverlaySseController(RuntimeOverlayConfigService configService, OverlayContentService contentService,
			ObjectMapper objectMapper) {
		this.configService = configService;
		this.contentService = contentService;
		this.objectMapper = objectMapper;
	}

	@GetMapping(value = "/{type}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream(@PathVariable String type) {
		RuntimeOverlayType overlayType = parseType(type);
		SseEmitter emitter = new SseEmitter(0L);

		Consumer<RuntimeOverlayEvent> listener = event -> {
			try {
				emitter.send(SseEmitter.event()
						.name(event.eventType())
						.data(objectMapper.writeValueAsString(event), MediaType.APPLICATION_JSON));
			} catch (IOException e) {
				emitter.complete();
			}
		};

		configService.addListener(overlayType, listener);
		emitter.onCompletion(() -> configService.removeListener(overlayType, listener));
		emitter.onTimeout(() -> configService.removeListener(overlayType, listener));
		emitter.onError(e -> configService.removeListener(overlayType, listener));

		try {
			Map<String, Object> snapshot = Map.of(
					"type", overlayType.name(),
					"alerts", contentService.getRecentAlerts(),
					"giveaways", contentService.getRecentGiveaways(),
					"events", contentService.getRecentEvents());
			emitter.send(SseEmitter.event()
					.name("snapshot")
					.data(objectMapper.writeValueAsString(snapshot), MediaType.APPLICATION_JSON));
		} catch (IOException e) {
			emitter.complete();
		}

		return emitter;
	}

	private RuntimeOverlayType parseType(String type) {
		try {
			return RuntimeOverlayType.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new NotFoundException("Invalid overlay type: " + type);
		}
	}
}
