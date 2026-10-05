package com.alertops.flow_execution_engine.messaging;

import java.util.UUID;

public record EscalationStartFailureNotificationRequested(UUID escalationId) {
}
