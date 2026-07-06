package com.blaze.eventhub.event.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditLogStore implements AuditLogStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<AuditLog> ROW_MAPPER = new AuditLogRowMapper();

    public JdbcAuditLogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        jdbc.update("""
                MERGE INTO audit_log (id, action, entity_type, entity_id,
                    before_state, after_state, created_by, created_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                auditLog.id(),
                auditLog.action(),
                auditLog.entityType(),
                auditLog.entityId(),
                auditLog.beforeState(),
                auditLog.afterState(),
                auditLog.createdBy(),
                toTimestamp(auditLog.createdAt()));
        return auditLog;
    }

    @Override
    public List<AuditLog> findByEntity(String entityType, String entityId) {
        return jdbc.query(
                "SELECT * FROM audit_log WHERE entity_type = ? AND entity_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, entityType, entityId);
    }

    @Override
    public List<AuditLog> findByAction(String action) {
        return jdbc.query(
                "SELECT * FROM audit_log WHERE action = ? ORDER BY created_at DESC",
                ROW_MAPPER, action);
    }

    @Override
    public List<AuditLog> findAll() {
        return jdbc.query(
                "SELECT * FROM audit_log ORDER BY created_at DESC",
                ROW_MAPPER);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class AuditLogRowMapper implements RowMapper<AuditLog> {
        @Override
        public AuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AuditLog(
                    rs.getString("id"),
                    rs.getString("action"),
                    rs.getString("entity_type"),
                    rs.getString("entity_id"),
                    rs.getString("before_state"),
                    rs.getString("after_state"),
                    rs.getString("created_by"),
                    toInstant(rs.getTimestamp("created_at")));
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
