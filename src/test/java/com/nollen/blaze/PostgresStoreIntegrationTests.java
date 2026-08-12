package com.nollen.blaze;

import com.nollen.blaze.alert.Alert;
import com.nollen.blaze.alert.AlertCondition;
import com.nollen.blaze.alert.AlertRule;
import com.nollen.blaze.alert.AlertRuleStore;
import com.nollen.blaze.alert.AlertStore;
import com.nollen.blaze.channel.BlazeChannelConfig;
import com.nollen.blaze.channel.BlazeChannelConfigStore;
import com.nollen.blaze.events.BlazeEventsLogEntry;
import com.nollen.blaze.events.BlazeEventsLogStore;
import com.nollen.blaze.events.BlazeEventType;
import com.nollen.blaze.events.EventSubscriptionSnapshot;
import com.nollen.blaze.events.InMemoryEventSubscriptionStore;
import com.nollen.blaze.giveaway.Giveaway;
import com.nollen.blaze.giveaway.GiveawayEntry;
import com.nollen.blaze.giveaway.GiveawayStatus;
import com.nollen.blaze.giveaway.InMemoryGiveawayEntryStore;
import com.nollen.blaze.giveaway.InMemoryGiveawayStore;
import com.nollen.blaze.intake.LiveEvent;
import com.nollen.blaze.intake.LiveEventStatus;
import com.nollen.blaze.intake.LiveEventType;
import com.nollen.blaze.intake.LiveEventSource;
import com.nollen.blaze.intake.LiveEventStore;
import com.nollen.blaze.overlays.InMemoryOverlayRepository;
import com.nollen.blaze.overlays.Overlay;
import com.nollen.blaze.overlays.OverlayConfig;
import com.nollen.blaze.overlays.OverlayProfile;
import com.nollen.blaze.overlays.runtime.InMemoryRuntimeOverlayConfigStore;
import com.nollen.blaze.overlays.runtime.RuntimeOverlayConfig;
import com.nollen.blaze.overlays.runtime.RuntimeOverlayType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that run the store layer against a real PostgreSQL
 * container (via Testcontainers + Podman). These cover the JDBC path that
 * unit tests skip (they use the no-arg in-memory constructor).
 *
 * Uses @JdbcTest instead of @SpringBootTest to avoid the schema-init timing
 * race: OverlayService.seedDevData() (@PostConstruct) accesses the DB before
 * DataSourceInitializer applies schema.sql under @SpringBootTest. @JdbcTest
 * only loads @Repository beans — no @Service, so no seed-on-startup — and
 * DataSourceInitializer runs schema.sql before any test method.
 *
 * ponytail: validates standard-SQL UPDATE-then-INSERT upsert (H2→PostgreSQL
 * compatibility) — the original MERGE INTO ... KEY(id) was H2-only.
 * Also validates Instant→Timestamp conversion (PostgreSQL driver rejects raw
 * java.time.Instant in PreparedStatement.setObject, unlike H2).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional
@Import({LiveEventStore.class, AlertStore.class, AlertRuleStore.class,
		InMemoryGiveawayStore.class, InMemoryGiveawayEntryStore.class, InMemoryOverlayRepository.class,
		InMemoryRuntimeOverlayConfigStore.class, InMemoryEventSubscriptionStore.class,
		BlazeChannelConfigStore.class, BlazeEventsLogStore.class})
class PostgresStoreIntegrationTests {

	@Container
	static final PostgreSQLContainer<?> PG =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:15")
					.asCompatibleSubstituteFor(DockerImageName.parse("postgres")))
					.withDatabaseName("testdb")
					.withUsername("test")
					.withPassword("test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", PG::getJdbcUrl);
		registry.add("spring.datasource.username", PG::getUsername);
		registry.add("spring.datasource.password", PG::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.sql.init.mode", () -> "always");
	}

	@Autowired JdbcTemplate jdbc;
	@Autowired LiveEventStore liveEventStore;
	@Autowired AlertStore alertStore;
	@Autowired AlertRuleStore alertRuleStore;
	@Autowired InMemoryGiveawayStore giveawayStore;
	@Autowired InMemoryGiveawayEntryStore entryStore;
	@Autowired InMemoryOverlayRepository overlayRepository;
	@Autowired InMemoryRuntimeOverlayConfigStore runtimeOverlayConfigStore;
	@Autowired InMemoryEventSubscriptionStore subscriptionStore;
	@Autowired BlazeChannelConfigStore channelConfigStore;
	@Autowired BlazeEventsLogStore eventsLogStore;

	@Test
	void schemaInitializedAllTablesCreated() {
		Integer tableCount = jdbc.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
		assertNotNull(tableCount);
		assertTrue(tableCount >= 12, "Expected >= 12 tables (schema.sql), got " + tableCount);
	}

	@Test
	void liveEventStoreSaveFindUpsertOnPostgres() {
		LiveEvent event = new LiveEvent("pg-ev-1", LiveEventType.FOLLOW,
				LiveEventSource.MANUAL, LiveEventStatus.ACCEPTED,
				Map.of("amount", 5), Instant.now(), "dedup-1");
		liveEventStore.save(event);

		Optional<LiveEvent> found = liveEventStore.findById("pg-ev-1");
		assertTrue(found.isPresent());
		assertEquals(LiveEventType.FOLLOW, found.get().type());

		LiveEvent updated = new LiveEvent("pg-ev-1", LiveEventType.SUBSCRIPTION,
				LiveEventSource.MANUAL, LiveEventStatus.ACCEPTED,
				Map.of("amount", 10), Instant.now(), "dedup-2");
		liveEventStore.save(updated);

		Optional<LiveEvent> reFound = liveEventStore.findById("pg-ev-1");
		assertTrue(reFound.isPresent());
		assertEquals(LiveEventType.SUBSCRIPTION, reFound.get().type());
		assertEquals("dedup-2", reFound.get().dedupKey());
		assertEquals(1, liveEventStore.count(), "Upsert must not duplicate rows");
	}

	@Test
	void alertStoreSaveFindUpsertOnPostgres() {
		Alert alert = new Alert("pg-alert-1", "rule-1", "Test Rule",
				BlazeEventType.CHANNEL_FOLLOW, Instant.now(),
				"Test message", false, Map.of("key", "value"));
		alertStore.save(alert);

		Optional<Alert> found = alertStore.findById("pg-alert-1");
		assertTrue(found.isPresent());
		assertEquals("Test Rule", found.get().ruleName());

		Alert updated = new Alert("pg-alert-1", "rule-1", "Updated Rule",
				BlazeEventType.CHANNEL_FOLLOW, Instant.now(),
				"Updated message", true, Map.of("key", "updated"));
		alertStore.save(updated);

		Optional<Alert> reFound = alertStore.findById("pg-alert-1");
		assertTrue(reFound.isPresent());
		assertEquals("Updated Rule", reFound.get().ruleName());
		assertTrue(reFound.get().acknowledged());
		assertEquals(1, alertStore.count());
	}

	@Test
	void alertStoreListQueriesHonorLimitsOnPostgres() {
		// Exercises the JDBC LIMIT clauses added in round 50 on real PG
		// (H2 unit tests never run these paths — syntax errors would surface
		// only in production without this test).
		alertStore.save(new Alert("pg-la-1", "rule-la", "Rule A",
				BlazeEventType.CHANNEL_FOLLOW, Instant.ofEpochSecond(4_000_001),
				"unacked 1", false, Map.of()));
		alertStore.save(new Alert("pg-la-2", "rule-la", "Rule A",
				BlazeEventType.CHANNEL_FOLLOW, Instant.ofEpochSecond(4_000_002),
				"unacked 2", false, Map.of()));
		alertStore.save(new Alert("pg-la-3", "rule-la", "Rule A",
				BlazeEventType.CHANNEL_CHAT_MESSAGE, Instant.ofEpochSecond(4_000_003),
				"acked", true, Map.of()));

		List<Alert> active = alertStore.findActive();
		assertEquals(2, active.size());
		assertEquals("pg-la-2", active.getFirst().id(), "newest unacked first");

		List<Alert> byType = alertStore.findByEventType(BlazeEventType.CHANNEL_FOLLOW.id());
		assertEquals(2, byType.size());

		Optional<Alert> last = alertStore.findLastByRuleId("rule-la");
		assertTrue(last.isPresent());
		assertEquals("pg-la-3", last.get().id(), "LIMIT 1 must keep the newest row");
	}

	@Test
	void alertRuleStoreSaveFindOnPostgres() {
		AlertRule rule = new AlertRule("pg-rule-1", "Donation Alert",
				BlazeEventType.CHANNEL_CHAT_MESSAGE, AlertCondition.ALWAYS,
				0, "template", true, 5000);
		alertRuleStore.save(rule);

		Optional<AlertRule> found = alertRuleStore.findById("pg-rule-1");
		assertTrue(found.isPresent());
		assertEquals("Donation Alert", found.get().name());
		assertEquals(AlertCondition.ALWAYS, found.get().condition());
	}

	@Test
	void giveawayStoreSaveFindUpsertOnPostgres() {
		Giveaway giveaway = new Giveaway("pg-gw-1", "Test Giveaway",
				"A test", GiveawayStatus.DRAFT, 0, 100,
				Instant.now(), null, null, null, java.util.List.of());
		giveawayStore.save(giveaway);

		Optional<Giveaway> found = giveawayStore.findById("pg-gw-1");
		assertTrue(found.isPresent());
		assertEquals("Test Giveaway", found.get().title());

		Giveaway updated = new Giveaway("pg-gw-1", "Updated Giveaway",
				"Updated desc", GiveawayStatus.OPEN, 5, 100,
				Instant.now(), Instant.now(), null, null, java.util.List.of());
		giveawayStore.save(updated);

		Optional<Giveaway> reFound = giveawayStore.findById("pg-gw-1");
		assertTrue(reFound.isPresent());
		assertEquals("Updated Giveaway", reFound.get().title());
		assertEquals(GiveawayStatus.OPEN, reFound.get().status());
		assertEquals(1, giveawayStore.count());
	}

	@Test
	void overlayProfileSaveFindUpsertOnPostgres() {
		OverlayProfile profile = new OverlayProfile("pg-prof-1", "Test Profile",
				"A test profile", Instant.now(), Instant.now());
		overlayRepository.saveProfile(profile);

		Optional<OverlayProfile> found = overlayRepository.findProfile("pg-prof-1");
		assertTrue(found.isPresent());
		assertEquals("Test Profile", found.get().name());

		OverlayProfile updated = new OverlayProfile("pg-prof-1", "Updated Profile",
				"Updated desc", Instant.now(), Instant.now());
		overlayRepository.saveProfile(updated);

		Optional<OverlayProfile> reFound = overlayRepository.findProfile("pg-prof-1");
		assertTrue(reFound.isPresent());
		assertEquals("Updated Profile", reFound.get().name());
		assertEquals(1, overlayRepository.countProfiles());
	}

	@Test
	void overlaySaveFindOnPostgres() {
		OverlayProfile profile = new OverlayProfile("pg-prof-2", "Profile 2",
				"desc", Instant.now(), Instant.now());
		overlayRepository.saveProfile(profile);

		Overlay overlay = new Overlay("pg-ov-1", profile.id(), "Test Overlay",
				"demo", "token-pg-ov-1", true, OverlayConfig.defaultConfig(),
				java.util.List.of(), java.util.List.of(), Instant.now(), Instant.now());
		overlayRepository.saveOverlay(overlay);

		Optional<Overlay> found = overlayRepository.findOverlay("pg-ov-1");
		assertTrue(found.isPresent());
		assertEquals("Test Overlay", found.get().name());
		assertEquals("demo", found.get().type());
	}

	@Test
	void runtimeOverlayConfigSaveFindUpsertOnPostgres() {
		RuntimeOverlayConfig config = RuntimeOverlayConfig.defaults(RuntimeOverlayType.ALERT).withId("pg-rt-1");
		runtimeOverlayConfigStore.save(config);

		Optional<RuntimeOverlayConfig> found = runtimeOverlayConfigStore.findById("pg-rt-1");
		assertTrue(found.isPresent());
		assertEquals(RuntimeOverlayType.ALERT, found.get().type());
		assertEquals("Alert Overlay", found.get().name());

		RuntimeOverlayConfig updated = new RuntimeOverlayConfig("pg-rt-1", RuntimeOverlayType.ALERT,
				"Updated Overlay", false, 5000, ".x {}", 10, 20, 800, 400, 0.5);
		runtimeOverlayConfigStore.save(updated);

		Optional<RuntimeOverlayConfig> reFound = runtimeOverlayConfigStore.findById("pg-rt-1");
		assertTrue(reFound.isPresent());
		assertEquals("Updated Overlay", reFound.get().name());
		assertFalse(reFound.get().enabled());
		assertEquals(5000, reFound.get().refreshIntervalMs());
		assertEquals(1, runtimeOverlayConfigStore.count());
	}

	@Test
	void eventSubscriptionSaveFindUpsertOnPostgres() {
		EventSubscriptionSnapshot snapshot = new EventSubscriptionSnapshot(
				"pg-sub-1", BlazeEventType.CHANNEL_CHAT_MESSAGE, "1", "channel-pg", "session-pg", Instant.now());
		subscriptionStore.save(snapshot);

		assertTrue(subscriptionStore.findByChannelIdAndType("channel-pg", BlazeEventType.CHANNEL_CHAT_MESSAGE).isPresent());
		assertEquals(1, subscriptionStore.list().size());

		EventSubscriptionSnapshot updated = new EventSubscriptionSnapshot(
				"pg-sub-1", BlazeEventType.CHANNEL_FOLLOW, "2", "channel-pg", "session-pg-2", Instant.now());
		subscriptionStore.save(updated);

		assertEquals(1, subscriptionStore.list().size());
		assertEquals(BlazeEventType.CHANNEL_FOLLOW, subscriptionStore.list().getFirst().type());
		assertEquals("session-pg-2", subscriptionStore.list().getFirst().sessionId());
	}

	@Test
	void giveawayEntryStoreSaveFindUpsertOnPostgres() {
		GiveawayEntry entry = new GiveawayEntry(
				"pg-ent-1", "pg-gw-1", "Test Participant", Instant.now(), false, true);
		entryStore.save(entry);

		assertEquals(1, entryStore.findByGiveawayId("pg-gw-1").size());
		assertEquals("Test Participant", entryStore.findByGiveawayId("pg-gw-1").getFirst().participantName());
		assertEquals(1, entryStore.countByGiveawayId("pg-gw-1"));

		GiveawayEntry updated = new GiveawayEntry(
				"pg-ent-1", "pg-gw-1", "Updated Participant", Instant.now(), true, true);
		entryStore.save(updated);

		assertEquals(1, entryStore.findByGiveawayId("pg-gw-1").size());
		assertEquals("Updated Participant", entryStore.findByGiveawayId("pg-gw-1").getFirst().participantName());
		assertTrue(entryStore.findByGiveawayId("pg-gw-1").getFirst().selected());
	}

	@Test
	void giveawayEntryStoreReplaceAllAtomicOnPostgres() {
		// validate @Transactional replaceAllForGiveaway is atomic on PG
		List<GiveawayEntry> initial = List.of(
				new GiveawayEntry("pg-e1", "pg-gw-1", "A", Instant.now(), false, true),
				new GiveawayEntry("pg-e2", "pg-gw-1", "B", Instant.now(), false, true));
		entryStore.replaceAllForGiveaway("pg-gw-1", initial);
		assertEquals(2, entryStore.findByGiveawayId("pg-gw-1").size());

		List<GiveawayEntry> updated = List.of(
				new GiveawayEntry("pg-e1", "pg-gw-1", "A", Instant.now(), true, true),
				new GiveawayEntry("pg-e2", "pg-gw-1", "B", Instant.now(), true, true));
		entryStore.replaceAllForGiveaway("pg-gw-1", updated);

		List<GiveawayEntry> after = entryStore.findByGiveawayId("pg-gw-1");
		assertEquals(2, after.size());
		assertTrue(after.stream().allMatch(GiveawayEntry::selected));
	}

	@Test
	void blazeChannelConfigSaveFindUpsertOnPostgres() {
		BlazeChannelConfig config = new BlazeChannelConfig("pg-ch-1", "Test Channel", "ch-123", "blaze", true);
		channelConfigStore.save(config);

		BlazeChannelConfig found = channelConfigStore.findById("pg-ch-1");
		assertNotNull(found);
		assertEquals("Test Channel", found.name());
		assertEquals("ch-123", found.channelId());
		assertEquals("blaze", found.platform());
		assertTrue(found.monitored());

		// Upsert (same id) — UPDATE-then-INSERT path
		BlazeChannelConfig updated = new BlazeChannelConfig("pg-ch-1", "Updated Channel", "ch-456", "blaze", false);
		channelConfigStore.save(updated);

		BlazeChannelConfig reFound = channelConfigStore.findById("pg-ch-1");
		assertNotNull(reFound);
		assertEquals("Updated Channel", reFound.name());
		assertEquals("ch-456", reFound.channelId());
		assertFalse(reFound.monitored());
		assertEquals(1, channelConfigStore.count());
	}

	@Test
	void blazeChannelConfigDefaultsPlatformWhenNull() {
		// Compact constructor defaults null/blank platform to "blaze"
		BlazeChannelConfig config = new BlazeChannelConfig("pg-ch-2", "No Platform", "ch-789", null, false);
		channelConfigStore.save(config);

		BlazeChannelConfig found = channelConfigStore.findById("pg-ch-2");
		assertNotNull(found);
		assertEquals("blaze", found.platform());
	}

	@Test
	void blazeEventsLogStoreAppendFindTimestampsOnPostgres() {
		eventsLogStore.clear(); // clean slate — PostgresStoreIntegrationTests is @Transactional, rolled back per test

		// Use fixed epoch seconds (no nanos) to avoid PG microsecond truncation mismatch
		Instant now = Instant.ofEpochSecond(1_000_000);
		eventsLogStore.append(new BlazeEventsLogEntry("pg-log-1", now, "chat", "simulate", "msg1", null));
		eventsLogStore.append(new BlazeEventsLogEntry("pg-log-2", now.plusSeconds(10), "follow", "system", "msg2", "{\"key\":\"val\"}"));

		assertEquals(2, eventsLogStore.count());

		List<BlazeEventsLogEntry> all = eventsLogStore.listAll();
		assertEquals(2, all.size());

		// Filter by event type
		List<BlazeEventsLogEntry> byType = eventsLogStore.list("chat", null, 10);
		assertEquals(1, byType.size());
		assertEquals("chat", byType.getFirst().eventType());

		// Filter by source — case-insensitive
		List<BlazeEventsLogEntry> bySource = eventsLogStore.list(null, "simulate", 10);
		assertEquals(1, bySource.size());

		// Last timestamp — validates MAX(received_at) query with PG Timestamp
		Instant last = eventsLogStore.findLastTimestamp();
		assertNotNull(last);
		assertEquals(now.plusSeconds(10), last);
	}

	@Test
	void schemaCreatesHotPathIndexesOnPostgres() {
		// schema.sql must create the dedup/cooldown/log indexes on real PG —
		// existsByDedupKey runs on every ingested event, so a missing index
		// silently degrades to full table scans as live_events grows.
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
		assertTrue(indexes.contains("idx_live_events_dedup_key"), "missing idx_live_events_dedup_key: " + indexes);
		assertTrue(indexes.contains("idx_live_events_occurred_at"), "missing idx_live_events_occurred_at");
		assertTrue(indexes.contains("idx_alerts_rule_triggered"), "missing idx_alerts_rule_triggered");
		assertTrue(indexes.contains("idx_blaze_events_log_received_at"), "missing idx_blaze_events_log_received_at");
	}

	@Test
	void liveEventListIsBoundedOnPostgres() {
		int total = LiveEventStore.LIST_LIMIT + 10;
		for (int i = 0; i < total; i++) {
			liveEventStore.save(new LiveEvent("pg-cap-" + i, LiveEventType.TEST,
					LiveEventSource.SIMULATED, LiveEventStatus.ACCEPTED,
					Map.of(), Instant.ofEpochSecond(2_000_000L + i), "cap-dedup-" + i));
		}
		assertEquals(total, liveEventStore.count());
		// JDBC path honors LIMIT — the capped window keeps the newest rows
		List<LiveEvent> page = liveEventStore.listAll();
		assertEquals(LiveEventStore.LIST_LIMIT, page.size());
		assertEquals("pg-cap-" + (total - 1), page.getFirst().id());
	}

	@Test
	void liveEventRetentionDeletesRowsOlderThanWindowOnPostgres() {
		Instant now = Instant.now();
		// Recent rows (inside the 30-day window) — must survive retention.
		for (int i = 0; i < 5; i++) {
			liveEventStore.save(new LiveEvent("pg-keep-" + i, LiveEventType.TEST,
					LiveEventSource.SIMULATED, LiveEventStatus.ACCEPTED,
					Map.of(), now.minus(i, ChronoUnit.DAYS), "ret-keep-" + i));
		}
		// Fill the table with rows OLDER than the retention window, bypassing
		// the store so retention does not fire mid-batch (guard = 1000 rows).
		for (int i = 0; i < LiveEventStore.RETENTION_MIN_ROWS; i++) {
			jdbc.update(
					"INSERT INTO live_events (id, type, source, status, payload, occurred_at, dedup_key) VALUES (?, ?, ?, ?, ?, ?, ?)",
					"pg-old-" + i, LiveEventType.TEST.name(), LiveEventSource.SIMULATED.name(),
					LiveEventStatus.ACCEPTED.name(), "{}",
					Timestamp.from(now.minus(LiveEventStore.RETENTION_DAYS + 1L, ChronoUnit.DAYS)),
					"ret-old-" + i);
		}
		assertEquals(LiveEventStore.RETENTION_MIN_ROWS + 5, liveEventStore.count());

		// A fresh save crosses the guard and fires retention: only rows inside
		// the 30-day window survive (5 recent + the one just saved).
		liveEventStore.save(new LiveEvent("pg-new", LiveEventType.TEST,
				LiveEventSource.SIMULATED, LiveEventStatus.ACCEPTED,
				Map.of(), now, "ret-new"));

		assertEquals(6, liveEventStore.count());
		assertTrue(liveEventStore.findById("pg-new").isPresent());
		assertTrue(liveEventStore.findById("pg-keep-0").isPresent());
		assertTrue(liveEventStore.findById("pg-old-0").isEmpty());
		assertTrue(liveEventStore.findById("pg-old-999").isEmpty());
	}

	@Test
	void nullTimestampsReadBackAsEpochOnPostgres() {
		// The schema declares NOT NULL, but a corrupted or hand-edited row could
		// still carry NULL. The RowMappers must fall back to Instant.EPOCH (the
		// OLDEST possible value), not Instant.now() — with now() a corrupted row
		// would masquerade as the newest event and escape retention forever.
		// Relax the constraint inside this rolled-back transaction to simulate it.
		jdbc.execute("ALTER TABLE live_events ALTER COLUMN occurred_at DROP NOT NULL");
		jdbc.update(
				"INSERT INTO live_events (id, type, source, status, payload, occurred_at, dedup_key) VALUES (?, ?, ?, ?, ?, NULL, ?)",
				"pg-null-ts", LiveEventType.TEST.name(), LiveEventSource.SIMULATED.name(),
				LiveEventStatus.ACCEPTED.name(), "{}", "null-ts");
		LiveEvent event = liveEventStore.findById("pg-null-ts").orElseThrow();
		assertEquals(Instant.EPOCH, event.timestamp());
		// Corrupted row must sort as OLDEST (listings are newest-first).
		List<LiveEvent> page = liveEventStore.listAll();
		assertEquals("pg-null-ts", page.getLast().id());

		jdbc.execute("ALTER TABLE blaze_events_log ALTER COLUMN received_at DROP NOT NULL");
		jdbc.update(
				"INSERT INTO blaze_events_log (received_at, event_type, source, message, raw_payload, id) VALUES (NULL, ?, ?, ?, ?, ?)",
				"chat", "simulate", "corrupted-row", null, "pg-null-log");
		List<BlazeEventsLogEntry> logEntries = eventsLogStore.list(null, null, 10);
		assertEquals(1, logEntries.size());
		assertEquals(Instant.EPOCH, logEntries.getFirst().timestamp());
	}

	@Test
	void blazeEventsLogRetentionOnPostgres() {
		eventsLogStore.clear();
		int total = BlazeEventsLogStore.MAX_PERSISTED + 50;
		for (int i = 0; i < total; i++) {
			eventsLogStore.append(new BlazeEventsLogEntry(
					"pg-ret-" + i, Instant.ofEpochSecond(3_000_000L + i), "chat", "simulate", "msg" + i, null));
		}
		// JDBC path now trims to MAX_PERSISTED newest rows (previously grew forever)
		assertEquals(BlazeEventsLogStore.MAX_PERSISTED, eventsLogStore.count());
		Instant last = eventsLogStore.findLastTimestamp();
		assertEquals(Instant.ofEpochSecond(3_000_000L + total - 1), last);
	}
}
