package com.nollen.blaze;

import com.nollen.blaze.alert.Alert;
import com.nollen.blaze.alert.AlertCondition;
import com.nollen.blaze.alert.AlertRule;
import com.nollen.blaze.alert.AlertRuleStore;
import com.nollen.blaze.alert.AlertStore;
import com.nollen.blaze.events.BlazeEventType;
import com.nollen.blaze.giveaway.Giveaway;
import com.nollen.blaze.giveaway.GiveawayStatus;
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

import java.time.Instant;
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
		InMemoryGiveawayStore.class, InMemoryOverlayRepository.class})
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
	@Autowired InMemoryOverlayRepository overlayRepository;

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
}
