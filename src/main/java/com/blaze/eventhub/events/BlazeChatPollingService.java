package com.blaze.eventhub.events;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.blaze.BlazeApiClient;
import com.blaze.eventhub.blaze.BlazeApiException;
import com.blaze.eventhub.event.Event;
import com.blaze.eventhub.event.EventStatus;
import com.blaze.eventhub.event.EventStore;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.event.participant.BlazeChatMessageParser;
import com.blaze.eventhub.event.participant.InvalidChatPayloadException;
import com.blaze.eventhub.event.participant.CaptureResult;
import com.blaze.eventhub.event.participant.CaptureStatus;
import com.blaze.eventhub.event.participant.ChatEntryCandidate;
import com.blaze.eventhub.event.participant.EventParticipantCaptureService;
import com.blaze.eventhub.oauth.PersistentOAuthCredentialService;
import com.blaze.eventhub.oauth.TokenSnapshot;
import com.blaze.eventhub.config.BlazeProperties;

@Service
public class BlazeChatPollingService {

    private static final Logger log = LoggerFactory.getLogger(BlazeChatPollingService.class);

    private final EventStore eventStore;
    private final PersistentOAuthCredentialService credentialService;
    private final BlazeApiClient apiClient;
    private final BlazeChatMessageParser parser;
    private final EventParticipantCaptureService captureService;
    private final ChatPollingCursorStore cursorStore;
    private final Clock clock;
    private final BlazeProperties properties;
    private final Map<PollingTarget, ReentrantLock> targetLocks = new ConcurrentHashMap<>();

    @Value("${eventhub.blaze.chat-polling-enabled:true}")
    private boolean scheduledPollingEnabled = true;

    public BlazeChatPollingService(
            EventStore eventStore,
            PersistentOAuthCredentialService credentialService,
            BlazeApiClient apiClient,
            BlazeChatMessageParser parser,
            EventParticipantCaptureService captureService,
            ChatPollingCursorStore cursorStore,
            Clock clock,
            BlazeProperties properties) {
        this.eventStore = eventStore;
        this.credentialService = credentialService;
        this.apiClient = apiClient;
        this.parser = parser;
        this.captureService = captureService;
        this.cursorStore = cursorStore;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${eventhub.blaze.chat-poll-interval-ms:2000}")
    public void scheduledPoll() {
        if (scheduledPollingEnabled) {
            pollNow();
        }
    }

    public PollingCycleResult pollNow() {
        Map<PollingTarget, Event> targets = uniqueTargets(eventStore.findByStatus(EventStatus.OPEN));
        return pollTargets(targets);
    }

    public PollingCycleResult pollEvent(String eventId, String creatorMemberId) {
        return pollEventThen(eventId, creatorMemberId, Function.identity());
    }

    public <T> T pollEventThen(
            String eventId,
            String creatorMemberId,
            Function<PollingCycleResult, T> completion) {
        Event event = eventStore.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
        if (!event.creatorMemberId().equals(creatorMemberId)) {
            throw new ForbiddenException("Somente o criador pode sincronizar este evento.");
        }
        if (event.status() != EventStatus.OPEN && event.status() != EventStatus.FINALIZING) {
            return completion.apply(new PollingCycleResult(0, 0, 0, 0, 0));
        }
        PollingTarget target = new PollingTarget(event.creatorMemberId(), event.creatorChannelId());
        ReentrantLock targetLock = targetLocks.computeIfAbsent(target, ignored -> new ReentrantLock());
        targetLock.lock();
        try {
            Event current = eventStore.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
            if (!current.creatorMemberId().equals(creatorMemberId)) {
                throw new ForbiddenException("Somente o criador pode sincronizar este evento.");
            }
            if (current.status() != EventStatus.OPEN && current.status() != EventStatus.FINALIZING) {
                return completion.apply(new PollingCycleResult(0, 0, 0, 0, 0));
            }
            return completion.apply(pollTargetLocked(
                    target,
                    current.id(),
                    current.finalizationCutoffAt(),
                    current.openedAt()));
        } finally {
            targetLock.unlock();
        }
    }

    private PollingCycleResult pollTargets(Map<PollingTarget, Event> targets) {
        PollingCycleResult total = new PollingCycleResult(0, 0, 0, 0, 0);

        for (PollingTarget target : targets.keySet()) {
            ReentrantLock targetLock = targetLocks.computeIfAbsent(target, ignored -> new ReentrantLock());
            targetLock.lock();
            try {
                List<Event> currentEvents = eventStore.findCapturingByChannelId(target.channelId()).stream()
                        .filter(event -> event.creatorMemberId().equals(target.memberId()))
                        .toList();
                if (currentEvents.size() == 1) {
                    Event current = currentEvents.getFirst();
                    total = add(total, pollTargetLocked(
                            target,
                            current.id(),
                            current.status() == EventStatus.FINALIZING
                                    ? current.finalizationCutoffAt()
                                    : null,
                            current.openedAt()));
                } else if (currentEvents.size() > 1) {
                    log.error("Mais de um giveaway capturando no mesmo canal: channelId={}", target.channelId());
                    total = add(total, new PollingCycleResult(0, 0, 0, 0, 1));
                }
            } finally {
                targetLock.unlock();
            }
            try {
                Thread.sleep(100); // Prevent hammering the external API sequentially in a tight loop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return total;
    }

    private PollingCycleResult pollTargetLocked(
            PollingTarget target,
            String eventId,
            Instant maximumMessageTime,
            Instant openedAt) {
        Instant polledAt = Instant.now(clock);
        ChatPollingCursor persistedCursor = cursorStore.find(target.memberId(), target.channelId())
                .filter(cursor -> eventId.equals(cursor.eventId()))
                .orElse(null);
        String boundaryMessageId = persistedCursor == null ? null : persistedCursor.lastMessageId();
        try {
            TokenSnapshot token = credentialService.currentValid(target.memberId());
            ChatScanResult scan = fetchChatPages(
                    target,
                    token,
                    boundaryMessageId,
                    persistedCursor == null ? null : persistedCursor.scanCursor(),
                    persistedCursor == null ? null : persistedCursor.scanAnchorMessageId(),
                    maximumMessageTime,
                    openedAt,
                    polledAt);
            List<ChatEntryCandidate> unseen = withoutBoundary(
                    boundaryMessageId, scan.candidates());

            int accepted = 0;
            int duplicates = 0;
            for (ChatEntryCandidate candidate : unseen) {
                CaptureResult capture = captureService.capture(candidate);
                if (capture.status() == CaptureStatus.ACCEPTED) {
                    accepted++;
                } else if (capture.status() == CaptureStatus.DUPLICATE) {
                    duplicates++;
                }
            }

            if (!scan.coverageComplete()) {
                cursorStore.markBackfillProgress(
                        target.memberId(),
                        target.channelId(),
                        eventId,
                        boundaryMessageId,
                        scan.nextCursor(),
                        scan.anchorMessageId(),
                        polledAt);
                return new PollingCycleResult(1, unseen.size(), accepted, duplicates, 1);
            }

            String promotedMessageId = scan.anchorMessageId() != null
                    ? scan.anchorMessageId()
                    : boundaryMessageId;
            cursorStore.markSuccess(
                    target.memberId(), target.channelId(), eventId, promotedMessageId, polledAt);
            if (scan.resumedFromBackfill()) {
                cursorStore.markFailure(
                        target.memberId(),
                        target.channelId(),
                        eventId,
                        "CHAT_HEAD_REFRESH_REQUIRED",
                        polledAt);
                return new PollingCycleResult(1, unseen.size(), accepted, duplicates, 1);
            }
            return new PollingCycleResult(1, unseen.size(), accepted, duplicates, 0);
        } catch (RuntimeException failure) {
            String errorCode = safeErrorCode(failure);
            cursorStore.markFailure(
                    target.memberId(), target.channelId(), eventId, errorCode, polledAt);
            log.warn("Falha no polling do chat: channelId={}, code={}", target.channelId(), errorCode);
            return new PollingCycleResult(0, 0, 0, 0, 1);
        }
    }

    private ChatScanResult fetchChatPages(
            PollingTarget target,
            TokenSnapshot token,
            String boundaryMessageId,
            String resumeCursor,
            String persistedAnchorMessageId,
            Instant maximumMessageTime,
            Instant openedAt,
            Instant polledAt) {
        Map<String, ChatEntryCandidate> uniqueCandidates = new LinkedHashMap<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = resumeCursor;
        String anchorMessageId = persistedAnchorMessageId;
        if (cursor != null) {
            seenCursors.add(cursor);
        }

        for (int page = 0; page < properties.getChatMaxPagesPerPoll(); page++) {
            Map<String, Object> response = cursor == null
                    ? apiClient.getChatMessages(target.channelId(), token)
                    : apiClient.getChatMessages(target.channelId(), cursor, token);
            List<ChatEntryCandidate> pageCandidates = parser.parse(target.channelId(), response);
            for (ChatEntryCandidate candidate : pageCandidates) {
                if (isEligibleForEvent(candidate, openedAt, maximumMessageTime)) {
                    uniqueCandidates.putIfAbsent(candidate.messageId(), candidate);
                }
            }
            if (anchorMessageId == null && !uniqueCandidates.isEmpty()) {
                anchorMessageId = uniqueCandidates.values().iterator().next().messageId();
            }

            boolean reachedStoredCursor = boundaryMessageId != null && pageCandidates.stream()
                    .anyMatch(candidate -> boundaryMessageId.equals(candidate.messageId()));
            boolean reachedOpeningBoundary = boundaryMessageId == null
                    && openedAt != null
                    && pageCandidates.stream().anyMatch(candidate -> candidate.sentAt().isBefore(openedAt));
            if (reachedStoredCursor || reachedOpeningBoundary) {
                return new ChatScanResult(
                        List.copyOf(uniqueCandidates.values()), true, null,
                        anchorMessageId, resumeCursor != null);
            }

            PageCursor pageCursor = pageCursor(response);
            String nextCursor = pageCursor.nextCursor();
            if (nextCursor == null) {
                if (boundaryMessageId == null
                        && coverageWindowIsProvable(openedAt, polledAt)
                        && (pageCandidates.isEmpty() || pageCursor.present())) {
                    return new ChatScanResult(
                            List.copyOf(uniqueCandidates.values()), true, null,
                            anchorMessageId, resumeCursor != null);
                }
                throw new ChatHistoryGapException(
                        "O historico do chat terminou antes da fronteira comprovavel do evento");
            }
            if (!seenCursors.add(nextCursor)) {
                throw new InvalidChatPayloadException("A Blaze repetiu o cursor da paginacao do chat");
            }
            cursor = nextCursor;
        }

        return new ChatScanResult(
                List.copyOf(uniqueCandidates.values()), false, cursor,
                anchorMessageId, resumeCursor != null);
    }

    private boolean coverageWindowIsProvable(Instant openedAt, Instant polledAt) {
        return openedAt != null
                && !openedAt.isBefore(polledAt.minusMillis(properties.getChatHistoryCoverageMaxAgeMs()));
    }

    private static boolean isEligibleForEvent(
            ChatEntryCandidate candidate,
            Instant openedAt,
            Instant maximumMessageTime) {
        return (openedAt == null || !candidate.sentAt().isBefore(openedAt))
                && (maximumMessageTime == null || !candidate.sentAt().isAfter(maximumMessageTime));
    }

    private static PageCursor pageCursor(Map<String, Object> response) {
        if (response == null) {
            return new PageCursor(false, null);
        }
        Object dataValue = response.get("data");
        Map<?, ?> data = dataValue instanceof Map<?, ?> map ? map : Map.of();
        Object paginationValue = data.get("pagination");
        boolean paginationDeclared = data.containsKey("pagination");
        if (!paginationDeclared && response.containsKey("pagination")) {
            paginationValue = response.get("pagination");
            paginationDeclared = true;
        }
        if (paginationDeclared && !(paginationValue instanceof Map<?, ?>)) {
            throw new InvalidChatPayloadException("A Blaze retornou pagination em formato invalido");
        }

        if (paginationValue instanceof Map<?, ?> pagination) {
            for (String key : List.of("nextCursor", "next_cursor", "cursor")) {
                if (pagination.containsKey(key)) {
                    return new PageCursor(true, cleanCursor(pagination.get(key)));
                }
            }
            return new PageCursor(true, null);
        }

        for (String key : List.of("nextCursor", "next_cursor", "cursor")) {
            if (data.containsKey(key)) {
                return new PageCursor(true, cleanCursor(data.get(key)));
            }
            if (response.containsKey(key)) {
                return new PageCursor(true, cleanCursor(response.get(key)));
            }
        }
        return new PageCursor(false, null);
    }

    private static String cleanCursor(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static PollingCycleResult add(PollingCycleResult left, PollingCycleResult right) {
        return new PollingCycleResult(
                left.channelsPolled() + right.channelsPolled(),
                left.messagesSeen() + right.messagesSeen(),
                left.acceptedEntries() + right.acceptedEntries(),
                left.duplicateEntries() + right.duplicateEntries(),
                left.failures() + right.failures());
    }

    private static List<ChatEntryCandidate> withoutBoundary(
            String boundaryMessageId,
            List<ChatEntryCandidate> candidates) {
        if (boundaryMessageId == null) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> !boundaryMessageId.equals(candidate.messageId()))
                .toList();
    }

    private static Map<PollingTarget, Event> uniqueTargets(List<Event> events) {
        Map<PollingTarget, Event> targets = new LinkedHashMap<>();
        for (Event event : events) {
            PollingTarget target = new PollingTarget(event.creatorMemberId(), event.creatorChannelId());
            targets.putIfAbsent(target, event);
        }
        return targets;
    }

    private static String safeErrorCode(RuntimeException failure) {
        if (failure instanceof BlazeApiException apiFailure) {
            return "BLAZE_HTTP_" + apiFailure.status();
        }
        if (failure instanceof ChatHistoryGapException) {
            return "CHAT_HISTORY_GAP";
        }
        return failure.getClass().getSimpleName();
    }

    private record ChatScanResult(
            List<ChatEntryCandidate> candidates,
            boolean coverageComplete,
            String nextCursor,
            String anchorMessageId,
            boolean resumedFromBackfill) {
    }

    private record PageCursor(boolean present, String nextCursor) {
    }

    private record PollingTarget(String memberId, String channelId) {
    }
}
