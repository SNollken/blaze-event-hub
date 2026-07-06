package com.blaze.eventhub.event.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    List<AuditLog> getAll(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId) {
        if (entityType != null && entityId != null) {
            return auditService.getEntityHistory(entityType, entityId);
        }
        if (action != null) {
            return auditService.getActionHistory(action);
        }
        return auditService.getAll();
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    List<AuditLog> getEntityHistory(@PathVariable String entityType, @PathVariable String entityId) {
        return auditService.getEntityHistory(entityType, entityId);
    }
}
