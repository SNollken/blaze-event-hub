package com.nollen.blaze.overlays.runtime;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayContentServiceTests {

    private OverlayContentService service;

    @BeforeEach
    void setUp() {
        service = new OverlayContentService();
    }

    @Test
    void addsAndRetrievesAlerts() {
        service.addAlert(Map.of("message", "New follower!", "type", "FOLLOW"));
        assertThat(service.getRecentAlerts()).hasSize(1);
        Map<String, Object> alert = service.getRecentAlerts().get(0);
        assertThat(alert).containsKey("id");
        assertThat(alert).containsKey("timestamp");
        assertThat(((Map<?, ?>) alert.get("data")).get("message")).isEqualTo("New follower!");
    }

    @Test
    void addsAndRetrievesGiveaways() {
        service.addGiveaway(Map.of("title", "Summer Giveaway", "participants", 42));
        assertThat(service.getRecentGiveaways()).hasSize(1);
    }

    @Test
    void addsAndRetrievesEvents() {
        service.addEvent(Map.of("message", "Stream started", "eventType", "STREAM_START"));
        assertThat(service.getRecentEvents()).hasSize(1);
        Map<String, Object> event = service.getRecentEvents().get(0);
        assertThat(((Map<?, ?>) event.get("data")).get("eventType")).isEqualTo("STREAM_START");
    }

    @Test
    void generatesAlertHtml() {
        RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.ALERT);
        String html = service.generateAlertHtml(config);
        assertThat(html).contains("overlay-container");
        assertThat(html).contains("alertPopIn");
        assertThat(html).contains("alertFadeOut");
        assertThat(html).contains("data-overlay-type=\"alert\"");
    }

    @Test
    void generatesGiveawayHtml() {
        RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.GIVEAWAY);
        String html = service.generateGiveawayHtml(config);
        assertThat(html).contains("overlay-container");
        assertThat(html).contains("goldShimmer");
        assertThat(html).contains("winnerReveal");
        assertThat(html).contains("data-overlay-type=\"giveaway\"");
    }

    @Test
    void generatesEventsHtml() {
        RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.EVENTS);
        String html = service.generateEventsHtml(config);
        assertThat(html).contains("overlay-container");
        assertThat(html).contains("events-feed");
        assertThat(html).contains("eventSlideIn");
        assertThat(html).contains("data-overlay-type=\"events\"");
    }

    @Test
    void htmlIncludesCustomCss() {
        RuntimeOverlayConfig config = new RuntimeOverlayConfig(
                "test-id", RuntimeOverlayType.ALERT, "Test", true, 3000,
                ".custom { font-size: 24px; }", 0, 0, 400, 200, 1.0);
        String html = service.generateAlertHtml(config);
        assertThat(html).contains(".custom { font-size: 24px; }");
    }

    @Test
    void htmlIncludesRefreshInterval() {
        RuntimeOverlayConfig config = new RuntimeOverlayConfig(
                "test-id", RuntimeOverlayType.ALERT, "Test", true, 5000,
                "", 0, 0, 400, 200, 1.0);
        String html = service.generateAlertHtml(config);
        assertThat(html).contains("data-refresh-ms=\"5000\"");
    }

    @Test
    void htmlIsObsCompatible() {
        RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.ALERT);
        String html = service.generateAlertHtml(config);
        assertThat(html).contains("background: transparent");
        assertThat(html).contains("pointer-events: none");
        assertThat(html).contains("overflow: hidden");
    }

    @Test
    void eventsFeedHasColorCodedTypes() {
        RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.EVENTS);
        String html = service.generateEventsHtml(config);
        assertThat(html).contains("type-FOLLOW");
        assertThat(html).contains("type-SUBSCRIBE");
        assertThat(html).contains("type-GIFT");
        assertThat(html).contains("type-DONATION");
        assertThat(html).contains("type-RAID");
    }

    @Test
    void alertHasAutoDismissAfter5Seconds() {
        RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.ALERT);
        String html = service.generateAlertHtml(config);
        assertThat(html).contains("4.5s"); // animation delay for fadeOut
        assertThat(html).contains("5500"); // setTimeout for removal
    }
}
