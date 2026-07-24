package com.blaze.eventhub.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.config.BlazeProperties;
import com.blaze.eventhub.oauth.PersistentOAuthCredentialService;
import com.blaze.eventhub.oauth.TokenSnapshot;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

@Service
public class BlazeSocketIOClient implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BlazeSocketIOClient.class);

    private final BlazeProperties properties;
    private final PersistentOAuthCredentialService credentialService;
    private final BlazeEventsPipeline pipeline;

    private final java.util.Map<String, Socket> sockets = new ConcurrentHashMap<>();
    private final java.util.Map<String, String> socketToUserId = new ConcurrentHashMap<>();

    public BlazeSocketIOClient(
            BlazeProperties properties,
            PersistentOAuthCredentialService credentialService,
            BlazeEventsPipeline pipeline) {
        this.properties = properties;
        this.credentialService = credentialService;
        this.pipeline = pipeline;
    }

    /**
     * Connect to Blaze's Socket.IO server for a specific user.
     * Must be called after the user has authenticated via OAuth.
     */
    public synchronized void connect(String memberId) {
        if (sockets.containsKey(memberId)) {
            Socket existing = sockets.get(memberId);
            if (existing != null && existing.connected()) {
                log.debug("Socket already connected for member {}", memberId);
                return;
            } else {
                disconnect(memberId);
            }
        }

        log.info("Connecting to Blaze Socket.IO for member {}", memberId);

        // Get valid token for this user
        TokenSnapshot token = credentialService.currentValid(memberId);
        if (token == null || token.accessTokenBlank()) {
            throw new IllegalStateException(
                    "No valid access token for member " + memberId + ". User must authenticate first.");
        }

        try {
            IO.Options options = new IO.Options();
            options.transports = new String[]{"websocket", "polling"};
            options.reconnection = true;
            options.reconnectionAttempts = properties.getSocketReconnectAttempts();
            options.reconnectionDelay = properties.getSocketReconnectIntervalSec() * 1000L;
            options.reconnectionDelayMax = 30000;
            options.timeout = 20000;
            options.query = "token=" + token.accessToken();

            Socket socket = IO.socket(properties.getSocketUrl() + "/ws", options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                log.info("Socket.IO connected for member {}", memberId);
                subscribeToChannels(socket, memberId);
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                log.warn("Socket.IO disconnected for member {}: {}", memberId, args.length > 0 ? args[0] : "unknown");
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                log.error("Socket.IO connection error for member {}: {}", memberId, args.length > 0 ? args[0] : "unknown");
            });

            socket.on("channel.vote", createEventHandler("vote"));
            socket.on("channel.subscribe", createEventHandler("sub"));
            socket.on("channel.subscription.gift", createEventHandler("gifted_sub"));
            socket.on("channel.follow", createEventHandler("follow"));
            socket.on("channel.tip", createEventHandler("donation"));
            socket.on("channel.chat.message", createEventHandler("chat"));

            socket.connect();
            sockets.put(memberId, socket);

        } catch (Exception e) {
            log.error("Failed to create Socket.IO connection for member {}", memberId, e);
            throw new IllegalStateException("Failed to create Socket.IO connection", e);
        }
    }

    private void subscribeToChannels(Socket socket, String memberId) {
        List<String> channelIds = pipeline.getOpenChannelIdsForMember(memberId);

        if (channelIds.isEmpty()) {
            log.debug("No open channels for member {}", memberId);
            return;
        }

        for (String channelId : channelIds) {
            log.debug("Subscribing to channel {} for member {}", channelId, memberId);
            socket.emit("subscribe", channelId, new io.socket.client.Ack() {
                @Override
                @SuppressWarnings("unchecked")
                public void call(Object... args) {
                    if (args != null && args.length > 0 && args[0] instanceof Map) {
                        Map<String, Object> response = (Map<String, Object>) args[0];
                        if (Boolean.TRUE.equals(response.get("success"))) {
                            log.debug("Successfully subscribed to channel {}", channelId);
                        } else {
                            log.warn("Failed to subscribe to channel {}: {}", channelId, response.get("error"));
                        }
                    }
                }
            });
        }
    }

    private Emitter.Listener createEventHandler(String actionType) {
        return args -> {
            try {
                if (args == null || args.length == 0) {
                    log.debug("Received empty event for {}", actionType);
                    return;
                }

                Object payload = args[0];
                log.debug("Received {} event: {}", actionType, payload);

                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payloadMap = (Map<String, Object>) payload;
                    pipeline.processActionEvent(actionType, payloadMap);
                } else {
                    log.warn("Unexpected payload type {} for event {}", payload.getClass().getSimpleName(), actionType);
                }

            } catch (Exception e) {
                log.error("Error processing {} event", actionType, e);
            }
        };
    }

    public synchronized void disconnect(String memberId) {
        Socket socket = sockets.remove(memberId);
        if (socket != null) {
            socketToUserId.remove(socket.id());
            try {
                socket.disconnect();
                socket.close();
            } catch (Exception e) {
                log.warn("Error closing socket for member {}", memberId, e);
            }
            log.info("Disconnected Socket.IO for member {}", memberId);
        }
    }

    public boolean isConnected(String memberId) {
        Socket socket = sockets.get(memberId);
        return socket != null && socket.connected();
    }

    public int connectedCount() {
        return (int) sockets.values().stream().filter(Socket::connected).count();
    }

    @Override
    public void destroy() {
        log.info("Shutting down all Socket.IO connections ({} active)", sockets.size());
        sockets.keySet().forEach(this::disconnect);
    }
}