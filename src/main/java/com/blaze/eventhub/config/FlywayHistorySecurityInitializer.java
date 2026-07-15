package com.blaze.eventhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
final class FlywayHistorySecurityInitializer implements ApplicationRunner {

    static final String RLS_STATUS_SQL = """
            SELECT relrowsecurity
            FROM pg_class
            WHERE oid = 'public.flyway_schema_history'::regclass
            """;
    static final String ENABLE_RLS_SQL =
            "ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY";

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayHistorySecurityInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    FlywayHistorySecurityInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean rlsEnabled = jdbcTemplate.queryForObject(RLS_STATUS_SQL, Boolean.class);
        if (Boolean.TRUE.equals(rlsEnabled)) {
            return;
        }

        jdbcTemplate.execute(ENABLE_RLS_SQL);
        LOGGER.info("RLS habilitado no historico de migrations do Flyway");
    }
}
