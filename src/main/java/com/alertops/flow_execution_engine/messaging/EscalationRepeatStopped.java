package com.alertops.flow_execution_engine.messaging;

import java.util.UUID;

/** Requests cancellation of an original escalation's repeat wake-up. */
public record EscalationRepeatStopped(UUID escalationId) {
}
