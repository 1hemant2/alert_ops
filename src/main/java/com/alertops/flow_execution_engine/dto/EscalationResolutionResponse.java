package com.alertops.flow_execution_engine.dto;

import java.time.Instant;

public record EscalationResolutionResponse(
        String escalationName,
        String status,
        String resolvedBy,
        Instant resolvedAt,
        boolean alreadyResolved) {
}
