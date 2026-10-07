package com.alertops.flow_execution_engine.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditActorType;

/** Safe API representation of one saved escalation activity event. */
public record EscalationHistoryEventResponse(
        UUID id,
        AuditAction action,
        String previousState,
        String newState,
        AuditActorType actorType,
        UUID actorId,
        String actorEmail,
        Instant occurredAt,
        String reason,
        Map<String, String> details) {
}
