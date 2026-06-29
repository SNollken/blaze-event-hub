package com.nollen.blaze.overlays.runtime;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OverlayHtmlController {

    private final OverlayContentService contentService;
    private final RuntimeOverlayConfigService configService;

    public OverlayHtmlController(OverlayContentService contentService, RuntimeOverlayConfigService configService) {
        this.contentService = contentService;
        this.configService = configService;
    }

    @GetMapping("/overlay/alerts")
    ResponseEntity<String> alerts() {
        RuntimeOverlayConfig config = getConfigForType(RuntimeOverlayType.ALERT);
        String html = contentService.generateAlertHtml(config);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/overlay/giveaways")
    ResponseEntity<String> giveaways() {
        RuntimeOverlayConfig config = getConfigForType(RuntimeOverlayType.GIVEAWAY);
        String html = contentService.generateGiveawayHtml(config);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/overlay/events")
    ResponseEntity<String> events() {
        RuntimeOverlayConfig config = getConfigForType(RuntimeOverlayType.EVENTS);
        String html = contentService.generateEventsHtml(config);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private RuntimeOverlayConfig getConfigForType(RuntimeOverlayType type) {
        List<RuntimeOverlayConfig> configs = configService.listAll();
        return configs.stream()
                .filter(c -> c.type() == type)
                .findFirst()
                .orElse(RuntimeOverlayConfig.defaults(type));
    }
}
