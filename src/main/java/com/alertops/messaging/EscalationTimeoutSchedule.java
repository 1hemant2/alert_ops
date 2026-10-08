package com.alertops.messaging;

import java.time.Instant;
import java.util.UUID;

public record EscalationTimeoutSchedule(
        UUID escalationId,
        UUID executionStepId,
        EscalationTimeoutType type,
        Instant dueAt) {
}
