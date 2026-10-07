package com.alertops.flow_execution_engine.dto;

import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record ExecutionStateResponseDto(
        UUID id,
        UUID nodeId,
        BigInteger position,
        String userEmail,
        FlowExecutionStepStatus status,
        int sendAttemptCount,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt
) {
    // Converts one durable execution step into the API timeline shape.
    public static ExecutionStateResponseDto from(FlowExecutionState state) {
        return new ExecutionStateResponseDto(
                state.getId(),
                state.getNodeId(),
                state.getPosition(),
                state.getUserEmail(),
                state.getStatus(),
                state.getSendAttemptCount(),
                state.getDueAt(),
                state.getCreatedAt(),
                state.getUpdatedAt()
        );
    }
}
