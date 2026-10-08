package com.alertops.flow_execution_engine.dto;

import java.time.Instant;
import java.util.UUID;

public record EscalationManualActionResponse(
        String escalationName,
        String status,
        UUID sourceStepId,
        String sourceRecipientEmail,
        UUID targetStepId,
        String targetRecipientEmail,
        Instant actionDeadline,
        boolean actionAvailable,
        boolean alreadyEscalated,
        String unavailableReason) {
}
