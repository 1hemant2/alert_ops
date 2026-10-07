package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

class EscalationTimeoutServiceTest {
    private static final UUID ESCALATION_ID = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID ACKNOWLEDGED_STEP_ID = UUID.fromString("73000000-0000-0000-0000-000000000002");
    private static final UUID NEXT_STEP_ID = UUID.fromString("73000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private final EscalationRepository escalationRepository = org.mockito.Mockito.mock(EscalationRepository.class);
    private final FlowExecutionStateRepository stateRepository = org.mockito.Mockito.mock(FlowExecutionStateRepository.class);
    private final StepSchedulingService stepSchedulingService = org.mockito.Mockito.mock(StepSchedulingService.class);
    private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
    private final EscalationTimeoutService service = new EscalationTimeoutService(
            escalationRepository, stateRepository, stepSchedulingService, auditService, eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC), 100);

    private Escalation escalation;
    private FlowExecutionState acknowledgedStep;

    @BeforeEach
    // Creates an open run with one expired final sent step for each scenario.
    void setUp() {
        escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setStatus(EscalationStatus.OPEN);

        acknowledgedStep = buildStep(ACKNOWLEDGED_STEP_ID, FlowExecutionStepStatus.SENT, 1);
        acknowledgedStep.setDueAt(NOW.minusSeconds(1));

        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));
        when(stateRepository.findByIdForUpdate(ACKNOWLEDGED_STEP_ID)).thenReturn(Optional.of(acknowledgedStep));
        when(stateRepository.findAllByProcessIdOrderByPositionAsc(ESCALATION_ID))
                .thenReturn(List.of(acknowledgedStep));
    }

    @Test
    // Verifies that an expired final acknowledgement exhausts the run.
    void expiredFinalAcknowledgementCompletesAsExhausted() {
        service.handleTimeout(new EscalationTimeoutSchedule(
                ESCALATION_ID, ACKNOWLEDGED_STEP_ID,
                EscalationTimeoutType.ACKNOWLEDGEMENT, acknowledgedStep.getDueAt()));

        assertEquals(EscalationStatus.COMPLETED, escalation.getStatus());
        assertEquals(EscalationResolutionType.EXHAUSTED, escalation.getResolutionType());
        verify(escalationRepository).save(escalation);
        verify(stepSchedulingService, never()).scheduleStepImmediately(any(), any());
        verifyAudit(AuditAction.ACKNOWLEDGEMENT_EXPIRED, EscalationStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(longs = {-60, 0, 60})
    // Verifies that resolution expiry schedules the next step at the current time.
    void expiredResolutionSchedulesNextStepNow(long offsetSeconds) {
        escalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        escalation.setAcknowledgedStepId(ACKNOWLEDGED_STEP_ID);
        escalation.setResolutionDeadline(NOW);

        FlowExecutionState nextStep = buildStep(NEXT_STEP_ID, FlowExecutionStepStatus.PAUSED, 2);
        nextStep.setDueAt(NOW.plusSeconds(offsetSeconds));
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID,
                List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED)))
                .thenReturn(nextStep);

        service.handleTimeout(new EscalationTimeoutSchedule(
                ESCALATION_ID, ACKNOWLEDGED_STEP_ID,
                EscalationTimeoutType.RESOLUTION, NOW));

        assertEquals(EscalationStatus.OPEN, escalation.getStatus());
        assertNull(escalation.getResolutionType());
        assertNull(escalation.getIssueSolvedBy());
        assertNull(escalation.getAcknowledgedAt());
        assertNull(escalation.getAcknowledgedStepId());
        assertNull(escalation.getResolutionDeadline());
        verify(stepSchedulingService).scheduleStepImmediately(nextStep, NOW);
        verifyAudit(AuditAction.RESOLUTION_EXPIRED, EscalationStatus.OPEN);
    }

    @Test
    // Verifies that a final resolution expiry exhausts and clears ownership.
    void expiredFinalResolutionCompletesAsExhaustedAndClearsOwnership() {
        escalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        escalation.setIssueSolvedBy("oncall@example.com");
        escalation.setAcknowledgedAt(NOW.minusSeconds(30));
        escalation.setAcknowledgedStepId(ACKNOWLEDGED_STEP_ID);
        escalation.setResolutionDeadline(NOW);

        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID,
                List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED)))
                .thenReturn(null);

        service.handleTimeout(new EscalationTimeoutSchedule(
                ESCALATION_ID, ACKNOWLEDGED_STEP_ID,
                EscalationTimeoutType.RESOLUTION, NOW));

        assertEquals(EscalationStatus.COMPLETED, escalation.getStatus());
        assertEquals(EscalationResolutionType.EXHAUSTED, escalation.getResolutionType());
        assertNull(escalation.getIssueSolvedBy());
        verify(stepSchedulingService, never()).scheduleStepImmediately(any(), any());
        verifyAudit(AuditAction.RESOLUTION_EXPIRED, EscalationStatus.COMPLETED);
    }

    @Test
    // Verifies that a stale resolution callback cannot repeat a completed transition.
    void staleResolutionTimeoutDoesNotRepeatACompletedTransition() {
        escalation.setStatus(EscalationStatus.RESOLVED);

        service.handleTimeout(new EscalationTimeoutSchedule(
                ESCALATION_ID, ACKNOWLEDGED_STEP_ID,
                EscalationTimeoutType.RESOLUTION, NOW.minusSeconds(1)));

        verify(escalationRepository, never()).save(any(Escalation.class));
        verifyNoAuditRecorded();
    }

    @Test
    // Verifies that restart recovery loads both durable timeout types.
    void recoveryLoadsAcknowledgementAndResolutionTimeouts() {
        Escalation acknowledged = new Escalation();
        acknowledged.setId(UUID.fromString("73000000-0000-0000-0000-000000000004"));
        acknowledged.setAcknowledgedStepId(ACKNOWLEDGED_STEP_ID);
        acknowledged.setResolutionDeadline(NOW.plusSeconds(30));
        acknowledged.setStatus(EscalationStatus.ACKNOWLEDGED);
        when(stateRepository.findOpenFinalAcknowledgementSteps(any()))
                .thenReturn(List.of(acknowledgedStep));
        when(escalationRepository.findPendingResolutionTimeouts(any())).thenReturn(List.of(acknowledged));

        List<EscalationTimeoutSchedule> recovered = service.loadPendingTimeouts();

        org.junit.jupiter.api.Assertions.assertEquals(2, recovered.size());
        org.junit.jupiter.api.Assertions.assertTrue(recovered.contains(new EscalationTimeoutSchedule(
                ESCALATION_ID, ACKNOWLEDGED_STEP_ID,
                EscalationTimeoutType.ACKNOWLEDGEMENT, acknowledgedStep.getDueAt())));
        org.junit.jupiter.api.Assertions.assertTrue(recovered.contains(new EscalationTimeoutSchedule(
                acknowledged.getId(), ACKNOWLEDGED_STEP_ID,
                EscalationTimeoutType.RESOLUTION, acknowledged.getResolutionDeadline())));
    }

    // Builds a runtime step with the supplied status and position.
    private FlowExecutionState buildStep(UUID id, FlowExecutionStepStatus status, int position) {
        FlowExecutionState state = new FlowExecutionState();
        state.setId(id);
        state.setProcessId(ESCALATION_ID);
        state.setStatus(status);
        state.setPosition(BigInteger.valueOf(position));
        return state;
    }

    // Verifies the recorded lifecycle audit transition.
    private void verifyAudit(AuditAction action, EscalationStatus newStatus) {
        org.mockito.ArgumentCaptor<AuditEvent> event = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals(action, event.getValue().action());
        assertEquals(EscalationStatus.ACKNOWLEDGED.name().equals(event.getValue().previousState())
                ? EscalationStatus.ACKNOWLEDGED.name() : EscalationStatus.OPEN.name(), event.getValue().previousState());
        assertEquals(newStatus.name(), event.getValue().newState());
    }

    // Verifies that stale callbacks do not write audit history.
    private void verifyNoAuditRecorded() {
        verify(auditService, never()).record(any(AuditEvent.class));
    }
}
