package com.blaze.eventhub.event;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.blaze.eventhub.common.IdGenerator;
import com.blaze.eventhub.common.NotFoundException;

@SpringBootTest
@ActiveProfiles("test")
class EventServiceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventService eventService;

    private final IdGenerator idGenerator = new IdGenerator();

    private static final String MEMBER_ID = "member-001";
    private static final String BLAZE_USER_ID = "blaze-user-001";
    private static final String CHANNEL_ID = "channel-001";

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM event_rules");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM members");
        // Ensure foreign key referential integrity
        jdbc.update("INSERT INTO members (id, blaze_user_id, blaze_username, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'active', NOW(), NOW())",
                MEMBER_ID, BLAZE_USER_ID, "testuser", "Test User");
    }

    @Test
    void createEvent_withValidRequest_createsSuccessfully() {
        CreateEventRequest request = buildCreateRequest("Test Event", List.of(
                buildRule("vote", 1, 10)));

        EventResponse response = eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID);

        assertNotNull(response.id());
        assertEquals("Test Event", response.title());
        assertEquals("draft", response.status());
        assertEquals("tier", response.rulesMode());
        assertEquals(MEMBER_ID, response.creatorMemberId());
        assertEquals(1, response.rules().size());
        assertEquals("vote", response.rules().get(0).actionType());
    }

    @Test
    void createEvent_withoutTitle_throws() {
        CreateEventRequest request = buildCreateRequest("", List.of(buildRule("vote", 1, 5)));
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID));
    }

    @Test
    void createEvent_withoutRules_throws() {
        CreateEventRequest request = buildCreateRequest("No Rules", List.of());
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID));
    }

    @Test
    void createEvent_withInvalidThreshold_throws() {
        CreateEventRequest request = buildCreateRequest("Bad Rule", List.of(
                new CreateEventRuleRequest("vote", 0, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID));
    }

    @Test
    void createEvent_withInvalidEntries_throws() {
        CreateEventRequest request = buildCreateRequest("Bad Entries", List.of(
                new CreateEventRuleRequest("vote", 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID));
    }

    @Test
    void getEvent_existingEvent_returnsEvent() {
        CreateEventRequest request = buildCreateRequest("Find Me", List.of(buildRule("sub", 3, 50)));
        EventResponse created = eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID);

        EventResponse found = eventService.getEvent(created.id());

        assertEquals(created.id(), found.id());
        assertEquals("Find Me", found.title());
        assertEquals(1, found.rules().size());
    }

    @Test
    void getEvent_nonExistingEvent_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> eventService.getEvent("non-existing-id"));
    }

    @Test
    void openEvent_fromDraft_succeeds() {
        EventResponse created = createDefaultEvent("Open Me");

        EventResponse opened = eventService.openEvent(created.id(), MEMBER_ID);

        assertEquals("open", opened.status());
    }

    @Test
    void openEvent_notCreator_throws() {
        EventResponse created = createDefaultEvent("Not Yours");

        assertThrows(IllegalArgumentException.class,
                () -> eventService.openEvent(created.id(), "other-member"));
    }

    @Test
    void openEvent_notDraft_throws() {
        EventResponse created = createDefaultEvent("Open Me First");
        eventService.openEvent(created.id(), MEMBER_ID);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.openEvent(created.id(), MEMBER_ID));
    }

    @Test
    void closeEvent_fromOpen_succeeds() {
        EventResponse created = createDefaultEvent("Close Me");
        eventService.openEvent(created.id(), MEMBER_ID);

        EventResponse closed = eventService.closeEvent(created.id(), MEMBER_ID);

        assertEquals("closed", closed.status());
    }

    @Test
    void closeEvent_notOpen_throws() {
        EventResponse created = createDefaultEvent("Still Draft");
        assertThrows(IllegalArgumentException.class,
                () -> eventService.closeEvent(created.id(), MEMBER_ID));
    }

    @Test
    void cancelEvent_fromDraft_succeeds() {
        EventResponse created = createDefaultEvent("Cancel Me");

        EventResponse cancelled = eventService.cancelEvent(created.id(), MEMBER_ID);

        assertEquals("cancelled", cancelled.status());
    }

    @Test
    void cancelEvent_alreadyCompleted_throws() {
        EventResponse created = createDefaultEvent("Done Deal");
        jdbc.update("UPDATE events SET status = 'completed' WHERE id = ?", created.id());

        assertThrows(IllegalArgumentException.class,
                () -> eventService.cancelEvent(created.id(), MEMBER_ID));
    }

    @Test
    void addRule_toDraftEvent_succeeds() {
        EventResponse created = createDefaultEvent("Add Rule");
        CreateEventRuleRequest ruleReq = new CreateEventRuleRequest("gifted_sub", 5, 25);

        EventRuleResponse rule = eventService.addRule(created.id(), ruleReq, MEMBER_ID);

        assertNotNull(rule.id());
        assertEquals("gifted_sub", rule.actionType());
        assertEquals(5, rule.thresholdAmount());
        assertEquals(25, rule.entries());
        assertTrue(rule.isActive());
    }

    @Test
    void addRule_toOpenedEvent_throws() {
        EventResponse created = createDefaultEvent("No More Rules");
        eventService.openEvent(created.id(), MEMBER_ID);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.addRule(created.id(), buildRule("donation", 10, 5), MEMBER_ID));
    }

    @Test
    void updateRule_onDraftEvent_succeeds() {
        EventResponse created = createDefaultEvent("Update Rule");
        EventRuleResponse rule = eventService.addRule(created.id(), buildRule("vote", 1, 5), MEMBER_ID);

        UpdateEventRuleRequest updateReq = new UpdateEventRuleRequest("donation", 3, 15, false);
        EventRuleResponse updated = eventService.updateRule(created.id(), rule.id(), updateReq, MEMBER_ID);

        assertEquals("donation", updated.actionType());
        assertEquals(3, updated.thresholdAmount());
        assertEquals(15, updated.entries());
        assertFalse(updated.isActive());
    }

    @Test
    void removeRule_fromDraftEvent_succeeds() {
        EventResponse created = createDefaultEvent("Remove Rule");
        EventRuleResponse rule = eventService.addRule(created.id(), buildRule("manual", 1, 1), MEMBER_ID);

        assertDoesNotThrow(() -> eventService.removeRule(created.id(), rule.id(), MEMBER_ID));
    }

    @Test
    void removeRule_nonExistingRule_throwsNotFound() {
        EventResponse created = createDefaultEvent("No Such Rule");
        assertThrows(NotFoundException.class,
                () -> eventService.removeRule(created.id(), "fake-rule-id", MEMBER_ID));
    }

    @Test
    void listEvents_withStatusFilter_returnsFiltered() {
        createDefaultEvent("Draft 1");
        EventResponse opened = createDefaultEvent("Open 1");
        eventService.openEvent(opened.id(), MEMBER_ID);

        List<EventResponse> draftEvents = eventService.listEvents("draft");
        List<EventResponse> openEvents = eventService.listEvents("open");

        assertEquals(1, draftEvents.size());
        assertEquals(1, openEvents.size());
    }

    @Test
    void updateEvent_onDraftEvent_succeeds() {
        EventResponse created = createDefaultEvent("Original Title");

        UpdateEventRequest updateReq = new UpdateEventRequest("New Title", "New desc", "vip", "VIP Prize",
                "cumulative", 5, false, null, null);
        EventResponse updated = eventService.updateEvent(created.id(), updateReq, MEMBER_ID);

        assertEquals("New Title", updated.title());
        assertEquals("New desc", updated.description());
        assertEquals("cumulative", updated.rulesMode());
        assertEquals(5, updated.maxEntriesPerParticipant());
        assertFalse(updated.requiresInterestBeforeAction());
    }

    // --- Helpers ---

    private EventResponse createDefaultEvent(String title) {
        CreateEventRequest request = buildCreateRequest(title, List.of(buildRule("vote", 1, 10)));
        return eventService.createEvent(request, MEMBER_ID, BLAZE_USER_ID, CHANNEL_ID);
    }

    private static CreateEventRequest buildCreateRequest(String title, List<CreateEventRuleRequest> rules) {
        return new CreateEventRequest(title, "Description", "cash", "Cash prize",
                "tier", 0, true, null, null, rules);
    }

    private static CreateEventRuleRequest buildRule(String actionType, int threshold, int entries) {
        return new CreateEventRuleRequest(actionType, threshold, entries);
    }
}
