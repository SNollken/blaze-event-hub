package com.blaze.eventhub.events;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blaze.eventhub.member.MemberService;

@RestController
@RequestMapping("/api/events/socket")
public class BlazeSocketController {

    private static final Logger log = LoggerFactory.getLogger(BlazeSocketController.class);

    private final BlazeSocketIOClient socketClient;
    private final BlazeEventsPipeline pipeline;
    private final MemberService memberService;

    public BlazeSocketController(
            BlazeSocketIOClient socketClient,
            BlazeEventsPipeline pipeline,
            MemberService memberService) {
        this.socketClient = socketClient;
        this.pipeline = pipeline;
        this.memberService = memberService;
    }

    /**
     * Connect the current user's Socket.IO to Blaze.
     * Called from the frontend when user wants to start real-time capture.
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect() {
        var member = memberService.getCurrentMember();
        String memberId = member.id();

        log.info("Connecting Socket.IO for member {}", memberId);

        // Get open channel IDs for this member
        List<String> channelIds = pipeline.getOpenChannelIdsForMember(member.id());
        
        if (channelIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Nenhum evento aberto encontrado para este canal"));
        }

        // Connect to Blaze Socket.IO
        socketClient.connect(memberId());

        // Subscribe to channel events for each open channel
        // (The BlazeSocketIOClient will handle subscriptions internally)

        return ResponseEntity.ok(Map.of(
                "status", "connected",
                "channelIds", channelIds
        ));
    }

    /**
     * Disconnect the current user's Socket.IO.
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        var member = memberService.getCurrentMember();
        
        log.info("Disconnecting Socket.IO for member {}", member.id());

        socketClient.disconnect(member.id());

        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    /**
     * Get connection status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        var member = memberService.getCurrentMember();
        
        boolean connected = socketClient.isConnected(member.id());

        return ResponseEntity.ok(Map.of(
                "connected", connected,
                "memberId", member.id()
        ));
    }

    /**
     * Manually trigger a test event (for debugging).
     */
    @PostMapping("/test-event")
    public ResponseEntity<Map<String, Object>> testEvent(@RequestBody Map<String, Object> payload) {
        String actionType = (String) payload.get("actionType");
        
        if (actionType == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "actionType is required"));
        }

        log.info("Test event received: {}", actionType);
        
        // Forward to pipeline for processing
        pipeline.processActionEvent(actionType, payload);
        
        return ResponseEntity.ok(Map.of("status", "processed"));
    }
}