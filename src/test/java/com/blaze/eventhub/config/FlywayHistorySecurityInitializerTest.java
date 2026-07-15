package com.blaze.eventhub.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayHistorySecurityInitializerTest {

    private JdbcTemplate jdbcTemplate;
    private FlywayHistorySecurityInitializer initializer;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        initializer = new FlywayHistorySecurityInitializer(jdbcTemplate);
    }

    @Test
    void enablesRlsAfterFlywayWhenItIsDisabled() {
        when(jdbcTemplate.queryForObject(
                FlywayHistorySecurityInitializer.RLS_STATUS_SQL,
                Boolean.class)).thenReturn(false);

        initializer.run(null);

        verify(jdbcTemplate).execute(FlywayHistorySecurityInitializer.ENABLE_RLS_SQL);
    }

    @Test
    void skipsDdlWhenRlsIsAlreadyEnabled() {
        when(jdbcTemplate.queryForObject(
                FlywayHistorySecurityInitializer.RLS_STATUS_SQL,
                Boolean.class)).thenReturn(true);

        initializer.run(null);

        verify(jdbcTemplate, never()).execute(FlywayHistorySecurityInitializer.ENABLE_RLS_SQL);
    }

    @Test
    void failsStartupWhenRlsCannotBeEnabled() {
        when(jdbcTemplate.queryForObject(
                FlywayHistorySecurityInitializer.RLS_STATUS_SQL,
                Boolean.class)).thenReturn(false);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(jdbcTemplate).execute(FlywayHistorySecurityInitializer.ENABLE_RLS_SQL);

        assertThrows(DataAccessResourceFailureException.class, () -> initializer.run(null));
    }
}
