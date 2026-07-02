package com.nollen.blaze.points;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nollen.blaze.common.IdGenerator;
import com.nollen.blaze.intake.LiveEvent;
import com.nollen.blaze.intake.LiveEventSource;
import com.nollen.blaze.intake.LiveEventStatus;
import com.nollen.blaze.intake.LiveEventType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointsServiceTests {

    private InMemoryPointsStore store;
    private PointsService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryPointsStore();
        service = new PointsService(store, new IdGenerator());

        // Seed default rules
        store.saveRule(new PointsRule("rule-follow", "FOLLOW", 10, "Follow na live", true));
        store.saveRule(new PointsRule("rule-sub", "SUBSCRIPTION", 100, "Inscricao", true));
        store.saveRule(new PointsRule("rule-donate", "DONATION", 50, "Doacao", true));
    }

    // ─── Helper ──────────────────────────────────────────────────────────

    private LiveEvent createEvent(String id, LiveEventType type, LiveEventStatus status,
                                  Map<String, Object> payload) {
        return new LiveEvent(
                id,
                type,
                LiveEventSource.SIMULATED,
                status,
                payload,
                Instant.now(),
                null
        );
    }

    // ─── Tests ───────────────────────────────────────────────────────────

    @Test
    void test_creditFromFollowEvent() {
        Map<String, Object> payload = Map.of("userId", "user1", "userName", "Alice");
        LiveEvent event = createEvent("evt-follow-1", LiveEventType.FOLLOW,
                LiveEventStatus.ACCEPTED, payload);

        Optional<PointsLedger> result = service.creditFromEvent(event);

        assertThat(result).isPresent();
        PointsLedger ledger = result.get();
        assertThat(ledger.points()).isEqualTo(10);
        assertThat(ledger.userId()).isEqualTo("user1");
        assertThat(ledger.username()).isEqualTo("Alice");
        assertThat(ledger.eventType()).isEqualTo("FOLLOW");
        assertThat(ledger.sourceEventId()).isEqualTo("evt-follow-1");
    }

    @Test
    void test_creditFromSubEvent() {
        Map<String, Object> payload = Map.of("userId", "user2", "userName", "Bob");
        LiveEvent event = createEvent("evt-sub-1", LiveEventType.SUBSCRIPTION,
                LiveEventStatus.ACCEPTED, payload);

        Optional<PointsLedger> result = service.creditFromEvent(event);

        assertThat(result).isPresent();
        assertThat(result.get().points()).isEqualTo(100);
        assertThat(result.get().userId()).isEqualTo("user2");
        assertThat(result.get().username()).isEqualTo("Bob");
        assertThat(result.get().eventType()).isEqualTo("SUBSCRIPTION");
    }

    @Test
    void test_duplicateEventNotCredited() {
        Map<String, Object> payload = Map.of("userId", "user1", "userName", "Alice");
        LiveEvent event = createEvent("evt-dup-1", LiveEventType.FOLLOW,
                LiveEventStatus.ACCEPTED, payload);

        Optional<PointsLedger> first = service.creditFromEvent(event);
        Optional<PointsLedger> second = service.creditFromEvent(event);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    void test_rejectedEventNotCredited() {
        Map<String, Object> payload = Map.of("userId", "user1", "userName", "Alice");
        LiveEvent event = createEvent("evt-rej-1", LiveEventType.FOLLOW,
                LiveEventStatus.REJECTED, payload);

        Optional<PointsLedger> result = service.creditFromEvent(event);

        assertThat(result).isEmpty();
    }

    @Test
    void test_unknownEventTypeNotCredited() {
        Map<String, Object> payload = Map.of("userId", "user1", "userName", "Alice");
        LiveEvent event = createEvent("evt-unknown-1", LiveEventType.TEST,
                LiveEventStatus.ACCEPTED, payload);

        Optional<PointsLedger> result = service.creditFromEvent(event);

        assertThat(result).isEmpty();
    }

    @Test
    void test_getBalance() {
        // Credit two events for same user: FOLLOW (10) + SUBSCRIPTION (100) = 110
        Map<String, Object> payload = Map.of("userId", "userA", "userName", "Alice");
        LiveEvent followEvent = createEvent("evt-bal-1", LiveEventType.FOLLOW,
                LiveEventStatus.ACCEPTED, payload);
        LiveEvent subEvent = createEvent("evt-bal-2", LiveEventType.SUBSCRIPTION,
                LiveEventStatus.ACCEPTED, payload);

        service.creditFromEvent(followEvent);
        service.creditFromEvent(subEvent);

        Optional<PointsBalance> balance = service.getBalance("userA");

        assertThat(balance).isPresent();
        assertThat(balance.get().totalPoints()).isEqualTo(110);
        assertThat(balance.get().transactionCount()).isEqualTo(2);
        assertThat(balance.get().userId()).isEqualTo("userA");
        assertThat(balance.get().username()).isEqualTo("Alice");
    }

    @Test
    void test_leaderboard() {
        // userA: FOLLOW (10)
        Map<String, Object> payloadA = Map.of("userId", "userA", "userName", "Alice");
        service.creditFromEvent(createEvent("evt-lb-1", LiveEventType.FOLLOW,
                LiveEventStatus.ACCEPTED, payloadA));

        // userB: SUBSCRIPTION (100) + FOLLOW (10) = 110
        Map<String, Object> payloadB = Map.of("userId", "userB", "userName", "Bob");
        service.creditFromEvent(createEvent("evt-lb-2", LiveEventType.SUBSCRIPTION,
                LiveEventStatus.ACCEPTED, payloadB));
        service.creditFromEvent(createEvent("evt-lb-3", LiveEventType.FOLLOW,
                LiveEventStatus.ACCEPTED, payloadB));

        // userC: DONATION (50)
        Map<String, Object> payloadC = Map.of("userId", "userC", "userName", "Charlie");
        service.creditFromEvent(createEvent("evt-lb-4", LiveEventType.DONATION,
                LiveEventStatus.ACCEPTED, payloadC));

        List<PointsLeaderboardEntry> leaderboard = service.getLeaderboard(20);

        assertThat(leaderboard).hasSize(3);
        // Sorted by total points desc: userB(110) > userC(50) > userA(10)
        assertThat(leaderboard.get(0).userId()).isEqualTo("userB");
        assertThat(leaderboard.get(0).totalPoints()).isEqualTo(110);
        assertThat(leaderboard.get(0).rank()).isEqualTo(1);

        assertThat(leaderboard.get(1).userId()).isEqualTo("userC");
        assertThat(leaderboard.get(1).totalPoints()).isEqualTo(50);
        assertThat(leaderboard.get(1).rank()).isEqualTo(2);

        assertThat(leaderboard.get(2).userId()).isEqualTo("userA");
        assertThat(leaderboard.get(2).totalPoints()).isEqualTo(10);
        assertThat(leaderboard.get(2).rank()).isEqualTo(3);
    }

    @Test
    void test_creditManual() {
        PointsLedger ledger = service.creditManual("userM", "ManualUser", 75, "Bonus");

        assertThat(ledger.points()).isEqualTo(75);
        assertThat(ledger.userId()).isEqualTo("userM");
        assertThat(ledger.username()).isEqualTo("ManualUser");
        assertThat(ledger.eventType()).isEqualTo("MANUAL");
        assertThat(ledger.sourceEventId()).isNull();

        // Verify it's stored and counted in balance
        Optional<PointsBalance> balance = service.getBalance("userM");
        assertThat(balance).isPresent();
        assertThat(balance.get().totalPoints()).isEqualTo(75);
        assertThat(balance.get().transactionCount()).isEqualTo(1);
    }

    @Test
    void test_disabledRuleNotCredited() {
        // Save a disabled rule for DONATION
        store.saveRule(new PointsRule("rule-donate-disabled", "DONATION", 50, "Disabled donation", false));

        Map<String, Object> payload = Map.of("userId", "userD", "userName", "Dave");
        LiveEvent event = createEvent("evt-dis-1", LiveEventType.DONATION,
                LiveEventStatus.ACCEPTED, payload);

        Optional<PointsLedger> result = service.creditFromEvent(event);

        assertThat(result).isEmpty();
    }
}
