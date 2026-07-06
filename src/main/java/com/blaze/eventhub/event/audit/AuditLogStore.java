package com.blaze.eventhub.event.audit;

import java.util.List;

public interface AuditLogStore {

    AuditLog save(AuditLog log);

    List<AuditLog> findByEntity(String entityType, String entityId);

    List<AuditLog> findByAction(String action);

    List<AuditLog> findAll();
}
