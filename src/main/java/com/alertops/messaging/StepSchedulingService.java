package com.alertops.messaging;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

@Service
public class StepSchedulingService {
    private final FlowExecutionStateRepository stateRepository;
    private final EscalationRepository escalationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int recoveryBatchSize;

    public StepSchedulingService(
            FlowExecutionStateRepository stateRepository,
            EscalationRepository escalationRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${alertops.scheduler.recovery-batch-size:10000}") int recoveryBatchSize
    ) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        if (recoveryBatchSize < 1) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Transactional
    public void schedule(FlowExecutionState state) {
        if (state == null) {
            throw new IllegalArgumentException("A response step is required");
        }
        Duration duration = state.getDuration();
        if (duration == null || duration.isNegative()) {
            throw new IllegalStateException("A response step requires a nonnegative wait duration");
        }

        state.setExecutionState("ACTIVE");
        state.setDueAt(Instant.now().plus(duration).truncatedTo(ChronoUnit.MICROS));
        state.setPublicationPending(true);
        FlowExecutionState saved = Objects.requireNonNull(stateRepository.save(state), "Saved response step is required");
        // The listener adds the timer only after this database save commits.
        eventPublisher.publishEvent(toSchedule(saved));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<EscalationStepSchedule> recoverPendingSchedules() {
        List<FlowExecutionState> pending = stateRepository.findPendingPublications(
                PageRequest.of(0, recoveryBatchSize));
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        return pending.stream()
                .filter(Objects::nonNull)
                .map(this::toSchedule)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(EscalationStepSchedule step) {
        if (step == null) {
            return;
        }
        UUID stepId = step.stepId();
        Instant dueAt = step.dueAt();
        if (stepId == null || dueAt == null) {
            return;
        }
        // A late confirmation for an older attempt must not clear a newly scheduled retry.
        stateRepository.markPublished(stepId, step.sendAttemptCount(), dueAt);
    }

    @Transactional
    public void rescheduleStepAtDueTime(FlowExecutionState state) {
        if (state == null) {
            return;
        }
        Instant requestedDueAt = state.getDueAt();
        UUID stateId = state.getId();
        if (requestedDueAt == null || stateId == null) {
            return;
        }

        FlowExecutionState current = stateRepository.findById(stateId).orElse(null);
        if (current == null) {
            return;
        }
        UUID processId = current.getProcessId();
        Instant currentDueAt = current.getDueAt();
        if (!"ACTIVE".equals(current.getExecutionState())
                || !"NOT_SENT".equals(current.getNotificationState())
                || current.getSendAttemptCount() != state.getSendAttemptCount()
                || currentDueAt == null
                || !requestedDueAt.equals(currentDueAt)
                || processId == null
                || !escalationRepository.findById(processId)
                        .map(this::isActive).orElse(false)) {
            return;
        }

        current.setPublicationPending(true);
        FlowExecutionState saved = Objects.requireNonNull(
                stateRepository.save(current), "Saved response step is required");
        // Re-register the saved timer after this transaction commits.
        eventPublisher.publishEvent(toSchedule(saved));
    }

    // Checks that this exact step attempt is still pending before scheduling its timer.
    @Transactional(readOnly = true)
    public boolean isStepStillPendingForPublication(EscalationStepSchedule schedule) {
        if (schedule == null) {
            return false;
        }
        UUID scheduleStepId = schedule.stepId();
        Instant scheduleDueAt = schedule.dueAt();
        if (scheduleStepId == null || scheduleDueAt == null) {
            return false;
        }
        FlowExecutionState current = stateRepository.findById(scheduleStepId).orElse(null);
        if (current == null) {
            return false;
        }
        UUID processId = current.getProcessId();
        if (!"ACTIVE".equals(current.getExecutionState())
                || !"NOT_SENT".equals(current.getNotificationState())
                || !current.isPublicationPending()
                || current.getSendAttemptCount() != schedule.sendAttemptCount()
                || !scheduleDueAt.equals(current.getDueAt())
                || processId == null) {
            return false;
        }
        return escalationRepository.findById(processId)
                .map(this::isActive)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public long countPendingSchedules() {
        return stateRepository.countPendingPublications();
    }

    private EscalationStepSchedule toSchedule(FlowExecutionState state) {
        FlowExecutionState nonNullState = Objects.requireNonNull(state, "Response step is required");
        return new EscalationStepSchedule(
                nonNullState.getId(), nonNullState.getSendAttemptCount(), nonNullState.getDueAt());
    }

    private boolean isActive(com.alertops.flow_execution_engine.model.Escalation escalation) {
        return escalation != null && "OPEN".equals(escalation.getStatus());
    }
}
