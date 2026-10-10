package com.alertops.flow_execution_engine.messaging;

import java.time.Instant;
import java.util.UUID;

/** Requests an in-memory wake-up for the original escalation's next repeat. */
public record EscalationRepeatSchedule(UUID escalationId, Instant nextRepeatAt) {
}
