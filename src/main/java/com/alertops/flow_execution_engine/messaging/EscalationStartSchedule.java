package com.alertops.flow_execution_engine.messaging;

import java.time.Instant;
import java.util.UUID;

public record EscalationStartSchedule(UUID escalationId, Instant scheduledStartAt) {
}
