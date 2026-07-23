package com.blaze.eventhub.event;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.ForbiddenException;
import com.blaze.eventhub.common.ConflictException;
import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.blaze.BlazeChannelResponse;
import com.blaze.eventhub.event.participant.ChatEntryMatcher;
import com.blaze.eventhub.event.participant.EventParticipant;
import com.blaze.eventhub.event.participant.EventParticipantStore;
import com.blaze.eventhub.event.participant.ParticipantPoolHasher;
import com.blaze.eventhub.events.ChatPollingCursor;
import com.blaze.eventhub.events.ChatPollingCursorStore;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final Set<EventStatus> PUBLIC_STATUSES = Set.of(
            EventStatus.OPEN,
            EventStatus.FINALIZING,
            EventStatus.CLOSED,
            EventStatus.COMPLETED);
    private static final Pattern ENTRY_COMMAND = Pattern.compile(
            "^![\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,78}$");
    private static final Set<String> X_POST_HOSTS = Set.of(
            "x.com",
            "www.x.com",
            "mobile.x.com",
            "twitter.com",
            "www.twitter.com",
            "mobile.twitter.com");
    private static final Pattern X_POST_PATH = Pattern.compile(
            "^/(?:[^/]+/status|i/web/status)/\\d+(?:/.*)?$");

    private final EventStore eventStore;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventParticipantStore participantStore;
    private final ChatPollingCursorStore cursorStore;
    private final EventActionRuleService actionRuleService;
    private final long pollingIntervalMillis;

    public EventService(
            EventStore eventStore,
            IdGenerator idGenerator,
            Clock clock,
            EventParticipantStore participantStore,
            ChatPollingCursorStore cursorStore,
            EventActionRuleService actionRuleService,
            @Value("${eventhub.blaze.chat-poll-interval-ms:2000}") long pollingIntervalMillis) {
        this.eventStore = eventStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.participantStore = participantStore;
        this.cursorStore = cursorStore;
        this.actionRuleService = actionRuleService;
        this.pollingIntervalMillis = pollingIntervalMillis;
    }

    public EventResponse createEvent(
            CreateEventRequest request,
            String creatorMemberId,
            String creatorBlazeUserId,
            BlazeChannelResponse creatorChannel) {
        validateCreateRequest(request);

        Instant startsAt = parseInstant(request.startsAt());
        Instant endsAt = parseInstant(request.endsAt());
        validateDateRange(startsAt, endsAt);
        String xPostUrl = normalizeAndValidateXPostUrl(request.xPostUrl());

        Instant now = Instant.now(clock);
        Event event = new Event(
                idGenerator.newId(),
                creatorMemberId,
                creatorBlazeUserId,
                creatorChannel.id(),
                creatorChannel.slug(),
                creatorChannel.displayName(),
                creatorChannel.avatarUrl(),
                request.title().trim(),
                request.description(),
                xPostUrl,
                request.prize().trim(),
                normalizeAndValidateCommand(request.entryCommand()),
                EventStatus.DRAFT,
                0,
                null,
                startsAt,
                endsAt,
                now,
                now,
                null,
                null,
                null,
                null,
                null);

        eventStore.insert(event);
        actionRuleService.initializeDefaults(event.id());
        log.info("Giveaway criado: id={}, creator={}", event.id(), creatorMemberId);
        return EventResponse.from(event, enabledActionTypes(event.id()));
    }

    public EventResponse getEvent(String eventId) {
        Event event = requireEvent(eventId);
        return EventResponse.from(event, enabledActionTypes(event.id()));
    }

    public EventResponse getVisibleEvent(String eventId, String viewerMemberId) {
        Event event = requireVisibleEvent(eventId, viewerMemberId);
        return EventResponse.from(event, enabledActionTypes(event.id()));
    }

    public List<EventResponse> listEvents(String statusFilter) {
        if (statusFilter != null && !statusFilter.isBlank()) {
            EventStatus status = EventStatus.fromDb(statusFilter);
            if (!PUBLIC_STATUSES.contains(status) && status != EventStatus.CANCELLED) {
                return List.of();
            }
            return eventStore.findByStatus(status).stream()
                    .filter(EventService::isPublicEvent)
                    .map(e -> EventResponse.from(e, enabledActionTypes(e.id())))
                    .toList();
        }
        return eventStore.findAll().stream()
                .filter(EventService::isPublicEvent)
                .map(e -> EventResponse.from(e, enabledActionTypes(e.id())))
                .toList();
    }

    public List<EventResponse> findByCreatorMemberId(String memberId) {
        return eventStore.findByCreatorMemberId(memberId).stream()
                .map(e -> EventResponse.from(e, enabledActionTypes(e.id())))
                .toList();
    }

    public Map<String, List<EventResponse>> getCreatorHistory(String memberId) {
        List<EventResponse> all = findByCreatorMemberId(memberId);
        List<EventResponse> drafts = all.stream()
                .filter(event -> "draft".equals(event.status()))
                .toList();
        List<EventResponse> active = all.stream()
                .filter(event -> "open".equals(event.status())
                        || "finalizing".equals(event.status())
                        || "closed".equals(event.status()))
                .toList();
        List<EventResponse> past = all.stream()
                .filter(event -> "completed".equals(event.status()) || "cancelled".equals(event.status()))
                .toList();
        return Map.of("drafts", drafts, "active", active, "past", past);
    }

    public EventLifecycleStats getEventStats(String eventId) {
        Event event = requireEvent(eventId);
        return lifecycleStats(event);
    }

    public EventLifecycleStats getVisibleEventStats(String eventId, String viewerMemberId) {
        return lifecycleStats(requireVisibleEvent(eventId, viewerMemberId));
    }

    private EventLifecycleStats lifecycleStats(Event event) {
        int participantCount = participantStore.countByEventId(event.id());
        boolean captureActive = event.status() == EventStatus.OPEN;
        boolean canFinalize = captureActive && participantCount > 0;
        boolean canDraw = event.status() == EventStatus.CLOSED
                && event.finalizedParticipantCount() > 0;
        ChatPollingCursor cursor = cursorStore
                .find(event.creatorMemberId(), event.creatorChannelId())
                .filter(candidate -> event.id().equals(candidate.eventId()))
                .orElse(null);
        String captureHealth = captureHealth(event, cursor);
        return new EventLifecycleStats(
                event.id(),
                event.status().name().toLowerCase(),
                participantCount,
                event.finalizedParticipantCount(),
                captureActive,
                canFinalize,
                canDraw,
                captureHealth,
                cursor == null ? null : cursor.lastPolledAt(),
                cursor == null ? null : cursor.lastSuccessAt(),
                cursor == null ? null : cursor.lastErrorCode(),
                event.openedAt(),
                event.finalizationCutoffAt(),
                event.closedAt(),
                event.completedAt());
    }

    private String captureHealth(Event event, ChatPollingCursor cursor) {
        if (event.status() == EventStatus.FINALIZING) {
            return "FINALIZING";
        }
        if (event.status() != EventStatus.OPEN) {
            return "INACTIVE";
        }
        if (cursor == null
                || cursor.lastSuccessAt() == null
                || (event.openedAt() != null && cursor.lastSuccessAt().isBefore(event.openedAt()))) {
            return "STARTING";
        }
        long staleAfterMillis = Math.max(pollingIntervalMillis * 3L, 20_000L);
        boolean stale = cursor.lastSuccessAt().isBefore(Instant.now(clock).minusMillis(staleAfterMillis));
        return cursor.lastErrorCode() != null || stale ? "DEGRADED" : "HEALTHY";
    }

    public List<EventParticipantResponse> getParticipants(String eventId, String memberId) {
        requireEventOwnership(eventId, memberId);
        return participantStore.findByEventId(eventId).stream()
                .map(EventParticipantResponse::from)
                .toList();
    }

    @Transactional
    public EventParticipantResponse addManualParticipant(String eventId, String memberId, String blazeUsername) {
        requireEventOwnership(eventId, memberId);
        String username = blazeUsername.trim();
        Instant now = Instant.now(clock);
        EventParticipant participant = new EventParticipant(
                idGenerator.newId(),
                eventId,
                username,
                username,
                username,
                null,
                ActionType.MANUAL.value(),
                1,
                now,
                now);
        if (!participantStore.saveIfAbsent(participant)) {
            throw new ConflictException("Participante '" + username + "' ja existe neste evento.");
        }
        return EventParticipantResponse.from(participant);
    }

    @Transactional
    public EventResponse updateEvent(String eventId, UpdateEventRequest request, String memberId) {
        Event event = eventStore.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
        assertOwner(event, memberId);
        assertDraft(event, "editar");

        String title = request.title() == null ? event.title() : request.title().trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("O titulo do evento e obrigatorio.");
        }

        String command = request.entryCommand() == null
                ? event.entryCommand()
                : normalizeAndValidateCommand(request.entryCommand());

        String prize = request.prize() == null ? event.prize() : request.prize().trim();
        if (prize.isBlank()) {
            throw new IllegalArgumentException("O premio e obrigatorio.");
        }

        String xPostUrl = request.xPostUrl() == null
                ? event.xPostUrl()
                : normalizeAndValidateXPostUrl(request.xPostUrl());

        Instant startsAt = request.startsAt() == null ? event.startsAt() : parseInstant(request.startsAt());
        Instant endsAt = request.endsAt() == null ? event.endsAt() : parseInstant(request.endsAt());
        validateDateRange(startsAt, endsAt);

        Event updated = new Event(
                event.id(),
                event.creatorMemberId(),
                event.creatorBlazeUserId(),
                event.creatorChannelId(),
                event.creatorChannelSlug(),
                event.creatorChannelDisplayName(),
                event.creatorChannelAvatarUrl(),
                title,
                request.description() == null ? event.description() : request.description(),
                xPostUrl,
                prize,
                command,
                event.status(),
                event.finalizedParticipantCount(),
                event.finalizedPoolHash(),
                startsAt,
                endsAt,
                event.createdAt(),
                Instant.now(clock),
                event.openedAt(),
                event.finalizationCutoffAt(),
                event.finalizationAttemptId(),
                event.closedAt(),
                event.completedAt());

        if (eventStore.updateDraft(updated) != 1) {
            throw new ConflictException("O evento mudou de estado durante a edicao.");
        }
        return EventResponse.from(updated, enabledActionTypes(updated.id()));
    }

    @Transactional
    public EventResponse openEvent(String eventId, String memberId) {
        Event event = requireEventOwnership(eventId, memberId);
        assertDraft(event, "abrir a captura");

        String channelId = event.creatorChannelId().trim();
        String captureKey = channelId;
        Instant openedAt = Instant.now(clock);
        try {
            if (eventStore.openEvent(eventId, captureKey, openedAt) != 1) {
                throw new ConflictException("O evento mudou de estado antes de abrir a captura.");
            }
        } catch (DataIntegrityViolationException conflict) {
            throw new IllegalArgumentException(
                    "Ja existe um giveaway em captura neste canal.",
                    conflict);
        }

        return getEvent(eventId);
    }

    @Transactional
    public FinalizationStart beginFinalization(String eventId, String memberId) {
        Event event = eventStore.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
        assertOwner(event, memberId);

        if (event.status() == EventStatus.CLOSED || event.status() == EventStatus.COMPLETED) {
            return new FinalizationStart(EventResponse.from(event, enabledActionTypes(event.id())), true, null);
        }
        if (event.status() == EventStatus.FINALIZING) {
            throw new ConflictException("Este evento ja esta sendo finalizado.");
        }
        if (event.status() != EventStatus.OPEN) {
            throw new IllegalArgumentException(
                    "Apenas eventos com captura aberta podem ser finalizados. Status: "
                            + event.status().name().toLowerCase());
        }

        Instant cutoffAt = Instant.now(clock);
        String attemptId = idGenerator.newId();
        if (eventStore.beginFinalization(eventId, cutoffAt, attemptId) != 1) {
            throw new ConflictException("Nao foi possivel iniciar a finalizacao do evento.");
        }
        return new FinalizationStart(getEvent(eventId), false, attemptId);
    }

    @Transactional
    public EventResponse completeFinalization(String eventId, String memberId, String attemptId) {
        Event event = eventStore.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
        assertOwner(event, memberId);
        if (event.status() == EventStatus.CLOSED || event.status() == EventStatus.COMPLETED) {
            return EventResponse.from(event, enabledActionTypes(event.id()));
        }
        if (event.status() != EventStatus.FINALIZING
                || event.finalizationCutoffAt() == null
                || attemptId == null
                || !attemptId.equals(event.finalizationAttemptId())) {
            throw new ConflictException("O evento nao possui uma finalizacao em andamento.");
        }

        List<EventParticipant> participants = participantStore.findByEventId(eventId);
        if (participants.isEmpty()) {
            throw new ConflictException(
                    "Nenhum participante entrou ainda. O evento foi reaberto e continua captando.");
        }
        String poolHash = ParticipantPoolHasher.sha256(participants);
        Instant closedAt = event.finalizationCutoffAt();
        if (eventStore.finalizeEvent(eventId, attemptId, closedAt, participants.size(), poolHash) != 1) {
            throw new ConflictException("A finalizacao nao atualizou exatamente um evento.");
        }

        return getEvent(eventId);
    }

    @Transactional
    public void abortFinalization(String eventId, String memberId, String attemptId) {
        Event event = eventStore.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
        assertOwner(event, memberId);
        if (event.status() == EventStatus.FINALIZING
                && attemptId != null
                && attemptId.equals(event.finalizationAttemptId())
                && eventStore.abortFinalization(eventId, attemptId, Instant.now(clock)) != 1) {
            throw new ConflictException("Nao foi possivel reabrir o evento apos a falha.");
        }
    }

    @Transactional
    public EventResponse cancelEvent(String eventId, String memberId) {
        Event event = eventStore.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
        assertOwner(event, memberId);
        if (event.status() != EventStatus.DRAFT && event.status() != EventStatus.OPEN) {
            throw new IllegalArgumentException(
                    "Somente rascunhos ou eventos em captura podem ser cancelados.");
        }
        if (eventStore.cancelEvent(eventId, Instant.now(clock)) != 1) {
            throw new ConflictException("O cancelamento nao atualizou exatamente um evento.");
        }
        return getEvent(eventId);
    }

    private Event requireEvent(String eventId) {
        return eventStore.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado: " + eventId));
    }

    private Event requireVisibleEvent(String eventId, String viewerMemberId) {
        Event event = requireEvent(eventId);
        if (isPublicEvent(event)
                || event.creatorMemberId().equals(viewerMemberId)) {
            return event;
        }
        throw new NotFoundException("Evento nao encontrado: " + eventId);
    }

    private Event requireEventOwnership(String eventId, String memberId) {
        Event event = requireEvent(eventId);
        assertOwner(event, memberId);
        return event;
    }

    private static void assertOwner(Event event, String memberId) {
        if (!event.creatorMemberId().equals(memberId)) {
            throw new ForbiddenException("Somente o criador pode realizar esta acao.");
        }
    }

    private static boolean isPublicEvent(Event event) {
        return PUBLIC_STATUSES.contains(event.status())
                || (event.status() == EventStatus.CANCELLED && event.openedAt() != null);
    }

    private static void assertDraft(Event event, String action) {
        if (event.status() != EventStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Somente rascunhos podem " + action + ". Status: "
                            + event.status().name().toLowerCase());
        }
    }

    private static void validateCreateRequest(CreateEventRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("O titulo do evento e obrigatorio.");
        }
        if (request.prize() == null || request.prize().isBlank()) {
            throw new IllegalArgumentException("O premio e obrigatorio.");
        }
        normalizeAndValidateCommand(request.entryCommand());
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Data invalida. Use o formato ISO-8601.", invalid);
        }
    }

    private static void validateDateRange(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("A data final precisa ser posterior a data inicial.");
        }
    }

    private static String normalizeAndValidateCommand(String value) {
        String command = ChatEntryMatcher.normalizeCommand(value);
        if (!ENTRY_COMMAND.matcher(command).matches()) {
            throw new IllegalArgumentException(
                    "O comando deve comecar com ! e usar apenas letras, numeros, _ ou -." );
        }
        return command;
    }

    private static String normalizeAndValidateXPostUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 2048) {
            throw new IllegalArgumentException("O link do post no X deve ter no maximo 2048 caracteres.");
        }
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            boolean validHost = host != null
                    && X_POST_HOSTS.contains(host.toLowerCase(Locale.ROOT));
            boolean validPort = uri.getPort() == -1 || uri.getPort() == 443;
            boolean validPath = uri.getPath() != null && X_POST_PATH.matcher(uri.getPath()).matches();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !validHost
                    || !validPort
                    || !validPath
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Informe um link HTTPS valido do X.");
            }
            return normalized;
        } catch (IllegalArgumentException invalidUrl) {
            if ("Informe um link HTTPS valido do X.".equals(invalidUrl.getMessage())) {
                throw invalidUrl;
            }
            throw new IllegalArgumentException("Informe um link HTTPS valido do X.", invalidUrl);
        }
    }

    private List<String> enabledActionTypes(String eventId) {
        return actionRuleService.enabledTypes(eventId).stream()
                .map(ActionType::value)
                .toList();
    }
}
