package com.blaze.eventhub.event.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.blaze.eventhub.common.IdGenerator;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogStore auditLogStore;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AuditService(AuditLogStore auditLogStore, IdGenerator idGenerator, Clock clock) {
        this.auditLogStore = auditLogStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public AuditLog log(String action, String entityType, String entityId,
            String beforeState, String afterState, String createdBy) {
        Instant now = Instant.now(clock);
        AuditLog auditLog = new AuditLog(
                idGenerator.newId(),
                action,
                entityType,
                entityId,
                beforeState,
                afterState,
                createdBy,
                now);

        auditLogStore.save(auditLog);

        log.info("Audit: action={}, entity={}/{}, by={}", action, entityType, entityId, createdBy);

        return auditLog;
    }

    public List<AuditLog> getEntityHistory(String entityType, String entityId) {
        return auditLogStore.findByEntity(entityType, entityId);
    }

    public List<AuditLog> getActionHistory(String action) {
        return auditLogStore.findByAction(action);
    }

    public List<AuditLog> getAll() {
        return auditLogStore.findAll();
    }
}
