package com.alertops.messaging;

import java.util.UUID;

public record EscalationTimeoutCancellation(
        UUID escalationId,
        EscalationTimeoutType type) {
}
