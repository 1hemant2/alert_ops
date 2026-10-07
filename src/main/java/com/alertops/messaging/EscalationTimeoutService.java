package com.alertops.messaging;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

/**
 * Applies what happens when an escalation waits too long.
 * Acknowledgement timeout exhausts an open escalation; resolution timeout
 * resumes the next step or exhausts the escalation. PostgreSQL is the source
 * of truth, and this class reloads it before every transition.
 */
@Service
public class EscalationTimeoutService {
    private final EscalationRepository escalationRepository;
    private final FlowExecutionStateRepository stateRepository;
    private final StepSchedulingService stepSchedulingService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final int recoveryBatchSize;

    // Creates the service that applies acknowledgement and resolution timeouts.
    public EscalationTimeoutService(
            EscalationRepository escalationRepository,
            FlowExecutionStateRepository stateRepository,
            StepSchedulingService stepSchedulingService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${alertops.scheduler.recovery-batch-size:10000}") int recoveryBatchSize) {
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.stepSchedulingService = Objects.requireNonNull(stepSchedulingService, "stepSchedulingService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (recoveryBatchSize < 1) {
            throw new IllegalArgumentException("Timeout recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    // Starts the wait for acknowledgement after the current transaction commits.
    public void scheduleAcknowledgementTimeout(UUID escalationId, UUID executionStepId, Instant dueAt) {
        publishAfterTransactionCommit(new EscalationTimeoutSchedule(
                requireIdentifier(escalationId, "escalationId"),
                requireIdentifier(executionStepId, "executionStepId"),
                EscalationTimeoutType.ACKNOWLEDGEMENT,
                Objects.requireNonNull(dueAt, "dueAt")));
    }

    // Starts the wait for resolution after the current transaction commits.
    public void scheduleResolutionTimeout(UUID escalationId, UUID executionStepId, Instant dueAt) {
        publishAfterTransactionCommit(new EscalationTimeoutSchedule(
                requireIdentifier(escalationId, "escalationId"),
                requireIdentifier(executionStepId, "executionStepId"),
                EscalationTimeoutType.RESOLUTION,
                Objects.requireNonNull(dueAt, "dueAt")));
    }

    // Stops the acknowledgement wait after the current transaction commits.
    public void cancelAcknowledgementTimeout(UUID escalationId) {
        cancelTimeoutWakeUp(escalationId, EscalationTimeoutType.ACKNOWLEDGEMENT);
    }

    // Stops the resolution wait after the current transaction commits.
    public void cancelResolutionTimeout(UUID escalationId) {
        cancelTimeoutWakeUp(escalationId, EscalationTimeoutType.RESOLUTION);
    }

    @Transactional(readOnly = true)
    // Loads saved waits that still need in-memory wake-up handles.
    public List<EscalationTimeoutSchedule> loadPendingTimeouts() {
        PageRequest page = PageRequest.of(0, recoveryBatchSize);
        List<EscalationTimeoutSchedule> schedules = new ArrayList<>();
        List<FlowExecutionState> finalStepsAwaitingAcknowledgement =
                stateRepository.findOpenFinalAcknowledgementSteps(page);
        if (finalStepsAwaitingAcknowledgement != null) {
            finalStepsAwaitingAcknowledgement.stream()
                    .filter(Objects::nonNull)
                    .filter(state -> state.getProcessId() != null && state.getId() != null && state.getDueAt() != null)
                    .map(state -> new EscalationTimeoutSchedule(
                            state.getProcessId(), state.getId(), EscalationTimeoutType.ACKNOWLEDGEMENT, state.getDueAt()))
                    .forEach(schedules::add);
        }
        List<Escalation> pendingResolutionEscalations = escalationRepository
                .findPendingResolutionTimeouts(page);
        if (pendingResolutionEscalations != null) {
            pendingResolutionEscalations.stream()
                    .filter(Objects::nonNull)
                    .filter(escalation -> escalation.getId() != null
                            && escalation.getAcknowledgedStepId() != null
                            && escalation.getResolutionDeadline() != null)
                    .map(escalation -> new EscalationTimeoutSchedule(
                            escalation.getId(),
                            escalation.getAcknowledgedStepId(),
                            EscalationTimeoutType.RESOLUTION,
                            escalation.getResolutionDeadline()))
                    .forEach(schedules::add);
        }
        return schedules;
    }

    @Transactional
    // Applies one timeout after reloading current locked database state.
    public void handleTimeout(EscalationTimeoutSchedule schedule) {
        if (!isValidTimeoutSchedule(schedule)) {
            return;
        }
        Instant now = clock.instant();
        if (schedule.dueAt().isAfter(now)) {
            return;
        }
        if (schedule.type() == EscalationTimeoutType.ACKNOWLEDGEMENT) {
            handleAcknowledgementTimeout(schedule, now);
        } else {
            handleResolutionTimeout(schedule, now);
        }
    }

    // Exhausts an open escalation whose final acknowledgement wait has ended.
    private void handleAcknowledgementTimeout(EscalationTimeoutSchedule schedule, Instant now) {
        Escalation escalation = findEscalationForUpdate(schedule.escalationId());
        if (escalation == null || escalation.getStatus() != EscalationStatus.OPEN) {
            return;
        }

        Optional<FlowExecutionState> sentStepResult = stateRepository.findByIdForUpdate(schedule.executionStepId());
        FlowExecutionState sentStep = sentStepResult == null ? null : sentStepResult.orElse(null);
        if (sentStep == null
                || sentStep.getStatus() != FlowExecutionStepStatus.SENT
                || !Objects.equals(sentStep.getProcessId(), escalation.getId())
                || !Objects.equals(sentStep.getDueAt(), schedule.dueAt())
                || hasLaterExecutionStep(sentStep)) {
            return;
        }

        escalation.setStatus(EscalationStatus.COMPLETED);
        escalation.setResolutionType(EscalationResolutionType.EXHAUSTED);
        escalationRepository.save(escalation);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                escalation.getId(),
                AuditAction.ACKNOWLEDGEMENT_EXPIRED,
                EscalationStatus.OPEN.name(),
                EscalationStatus.COMPLETED.name(),
                null,
                null,
                now,
                "ACKNOWLEDGEMENT_TIMEOUT",
                "executionStepId=" + sentStep.getId() + ";timeoutAt=" + schedule.dueAt()));
    }

    // Advances the next step or exhausts an escalation whose resolution wait has ended.
    private void handleResolutionTimeout(EscalationTimeoutSchedule schedule, Instant now) {
        Escalation escalation = findEscalationForUpdate(schedule.escalationId());
        if (escalation == null
                || escalation.getStatus() != EscalationStatus.ACKNOWLEDGED
                || !Objects.equals(escalation.getAcknowledgedStepId(), schedule.executionStepId())
                || !Objects.equals(escalation.getResolutionDeadline(), schedule.dueAt())) {
            return;
        }

        Optional<FlowExecutionState> acknowledgedStepResult = stateRepository
                .findByIdForUpdate(schedule.executionStepId());
        FlowExecutionState acknowledgedStep = acknowledgedStepResult == null
                ? null
                : acknowledgedStepResult.orElse(null);
        if (acknowledgedStep == null
                || acknowledgedStep.getStatus() != FlowExecutionStepStatus.SENT
                || !Objects.equals(acknowledgedStep.getProcessId(), escalation.getId())) {
            return;
        }

        FlowExecutionState nextStep = stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                escalation.getId(),
                List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED));
        EscalationStatus newStatus;
        EscalationResolutionType newResolutionType;
        if (nextStep == null) {
            newStatus = EscalationStatus.COMPLETED;
            newResolutionType = EscalationResolutionType.EXHAUSTED;
        } else {
            stepSchedulingService.scheduleStepImmediately(nextStep, now);
            newStatus = EscalationStatus.OPEN;
            newResolutionType = null;
        }

        escalation.setStatus(newStatus);
        escalation.setResolutionType(newResolutionType);
        escalation.setIssueSolvedBy(null);
        escalation.setAcknowledgedAt(null);
        escalation.setAcknowledgedStepId(null);
        escalation.setResolutionDeadline(null);
        escalationRepository.save(escalation);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                escalation.getId(),
                AuditAction.RESOLUTION_EXPIRED,
                EscalationStatus.ACKNOWLEDGED.name(),
                newStatus.name(),
                null,
                null,
                now,
                "RESOLUTION_TIMEOUT",
                "acknowledgedStepId=" + acknowledgedStep.getId()
                        + ";nextStepId=" + (nextStep == null ? "none" : nextStep.getId())
                        + ";timeoutAt=" + schedule.dueAt()));
    }

    // Checks whether a sent step has a later persisted execution step.
    private boolean hasLaterExecutionStep(FlowExecutionState source) {
        BigInteger sourcePosition = source.getPosition();
        if (sourcePosition == null || source.getProcessId() == null) {
            return true;
        }
        List<FlowExecutionState> states = stateRepository.findAllByProcessIdOrderByPositionAsc(source.getProcessId());
        if (states == null) {
            return true;
        }
        return states.stream()
                .filter(Objects::nonNull)
                .map(FlowExecutionState::getPosition)
                .filter(Objects::nonNull)
                .anyMatch(position -> position.compareTo(sourcePosition) > 0);
    }

    // Loads and locks the escalation before applying a timeout transition.
    private Escalation findEscalationForUpdate(UUID escalationId) {
        Optional<Escalation> escalationResult = escalationRepository.findByIdForUpdate(escalationId);
        return escalationResult == null ? null : escalationResult.orElse(null);
    }

    // Publishes a timeout wake-up cancellation after the current transaction commits.
    private void cancelTimeoutWakeUp(UUID escalationId, EscalationTimeoutType type) {
        publishAfterTransactionCommit(new EscalationTimeoutCancellation(
                requireIdentifier(escalationId, "escalationId"), type));
    }

    // Publishes an event immediately or after the surrounding transaction commits.
    private void publishAfterTransactionCommit(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            // Publishes the wake-up only after the database transaction succeeds.
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }

    // Checks whether a timeout schedule contains all required identifiers and time.
    private boolean isValidTimeoutSchedule(EscalationTimeoutSchedule schedule) {
        return schedule != null
                && schedule.escalationId() != null
                && schedule.executionStepId() != null
                && schedule.type() != null
                && schedule.dueAt() != null;
    }

    // Requires a non-null identifier before creating a timeout event.
    private UUID requireIdentifier(UUID value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
