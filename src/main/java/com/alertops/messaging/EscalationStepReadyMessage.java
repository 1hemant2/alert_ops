package com.alertops.messaging;

import java.time.Instant;
import java.util.UUID;

/** RabbitMQ message saying one escalation step is ready to run. */
public record EscalationStepReadyMessage(UUID stepId, int sendAttemptCount, Instant dueAt) {
}
