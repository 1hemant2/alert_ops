package com.alertops.flow_execution_engine.dto;

import java.util.UUID;

public record EscalationManualActionRequest(
        UUID expectedSourceStepId,
        UUID expectedTargetStepId) {
}
