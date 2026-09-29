package com.alertops.messaging;

import java.time.Instant;
import java.util.UUID;

public record EscalationStepSchedule(UUID stepId, int sendAttemptCount, Instant dueAt) {
}
