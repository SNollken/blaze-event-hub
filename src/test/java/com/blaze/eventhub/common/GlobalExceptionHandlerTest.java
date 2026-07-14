package com.blaze.eventhub.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void returnsActionableBadGatewayForFinalSynchronizationFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events/event-1/finalize");

        var response = handler.handleUpstreamUnavailable(
                new UpstreamUnavailableException("Chat indisponivel; o evento foi reaberto."),
                request);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("UPSTREAM_UNAVAILABLE", response.getBody().code());
        assertEquals("Chat indisponivel; o evento foi reaberto.", response.getBody().message());
    }

    @Test
    void sanitizesSecretsFromConflictMessages() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events/event-1/draw");

        var response = handler.handleConflict(
                new ConflictException("refresh_token=super-secret"),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertFalse(response.getBody().message().contains("super-secret"));
    }
}
