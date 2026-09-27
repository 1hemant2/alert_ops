package com.alertops.flow_execution_engine.dto;

import com.alertops.flow_execution_engine.model.FlowExecutionState;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record ExecutionStateResponseDto(
        UUID nodeId,
        BigInteger position,
        String userEmail,
        String executionState,
        String notificationState,
        int sendAttemptCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExecutionStateResponseDto from(FlowExecutionState state) {
        return new ExecutionStateResponseDto(
                state.getNodeId(),
                state.getPosition(),
                state.getUserEmail(),
                state.getExecutionState(),
                state.getNotificationState(),
                state.getSendAttemptCount(),
                state.getCreatedAt(),
                state.getUpdatedAt()
        );
    }
}
