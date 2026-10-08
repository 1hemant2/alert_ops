package com.alertops.flow_execution_engine.dto;

import java.time.Instant;

public record EscalationAcknowledgementResponse(
        String escalationName,
        String recipientEmail,
        String status,
        Instant expiresAt,
        Instant acknowledgedAt,
        String acknowledgedBy,
        boolean alreadyAcknowledged,
        Instant resolutionDeadline) {}
