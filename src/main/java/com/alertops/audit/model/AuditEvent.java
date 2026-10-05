package com.alertops.audit.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit command that can describe an event for any entity.
 * The reason is a short, safe explanation for the event; detailed exception
 * stack traces belong in application logs or metadata instead. State values
 * remain strings because each entity may define a different state vocabulary.
 */
public record AuditEvent(
        AuditEntityType entityType,
        UUID entityId,
        AuditAction action,
        String previousState,
        String newState,
        UUID userId,
        String userEmail,
        Instant occurredAt,
        String reason,
        String metadata) {
}
