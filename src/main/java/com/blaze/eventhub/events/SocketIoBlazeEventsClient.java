package com.blaze.eventhub.events;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.blaze.eventhub.config.BlazeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

@Component
@ConditionalOnProperty(prefix = "eventhub.blaze", name = "socket-enabled", havingValue = "true")
public class SocketIoBlazeEventsClient implements BlazeEventsClient {

    private static final Logger log = LoggerFactory.getLogger(SocketIoBlazeEventsClient.class);

    private final BlazeProperties blazeProperties;
    private final BlazeEventsRunner runner;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Socket socket;

    public SocketIoBlazeEventsClient(BlazeProperties blazeProperties, BlazeEventsRunner runner,
            ObjectMapper objectMapper) {
        this.blazeProperties = blazeProperties;
        this.runner = runner;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                connect();
            } catch (Exception e) {
                log.error("Failed to connect to Blaze Socket.IO", e);
                running.set(false);
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (socket != null) {
                socket.off();
                socket.disconnect();
                socket = null;
            }
            log.info("Blaze Socket.IO client stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && socket != null && socket.connected();
    }

    @SuppressWarnings("unchecked")
    private void connect() throws Exception {
        String url = blazeProperties.getSocketUrl();
        String path = blazeProperties.getSocketPath();

        IO.Options options = IO.Options.builder()
                .setPath(path)
                .setTransports(new String[]{"websocket"})
                .setReconnection(true)
                .setReconnectionAttempts(10)
                .setReconnectionDelay(3000)
                .build();

        socket = IO.socket(URI.create(url), options);

        socket.on(Socket.EVENT_CONNECT, args -> {
            log.info("Blaze Socket.IO connected to {}", url);
            subscribeToEvents();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            log.info("Blaze Socket.IO disconnected");
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            log.warn("Blaze Socket.IO connection error: {}", args.length > 0 ? args[0] : "unknown");
        });

        socket.on("eventsub", args -> {
            handleEventSub(args);
        });

        socket.connect();
    }

    @SuppressWarnings("unchecked")
    private void subscribeToEvents() {
        if (blazeProperties.isMonitoredChannelConfigured()) {
            String channelId = blazeProperties.getMonitoredChannelId();
            Map<String, String> subscribePayload = Map.of(
                    "id", "channel.events",
                    "version", "1.0.0",
                    "type", "subscribe",
                    "channelId", channelId);
            socket.emit("eventsub", subscribePayload);
            log.info("Subscribed to Blaze events for channel: {}", channelId);
        }
    }

    private void handleEventSub(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        try {
            Object data = args[0];
            BlazeEventEnvelope envelope;
            if (data instanceof String json) {
                envelope = objectMapper.readValue(json, BlazeEventEnvelope.class);
            } else if (data instanceof Map<?, ?> map) {
                envelope = objectMapper.convertValue(map, BlazeEventEnvelope.class);
            } else {
                return;
            }
            runner.acceptEnvelope(envelope);
        } catch (Exception e) {
            log.debug("Failed to parse Blaze event envelope: {}", e.getMessage());
        }
    }
}
