package com.blaze.eventhub.event.audit;

import java.time.Instant;

public record AuditLog(
        String id,
        String action,
        String entityType,
        String entityId,
        String beforeState,
        String afterState,
        String createdBy,
        Instant createdAt) {
}
