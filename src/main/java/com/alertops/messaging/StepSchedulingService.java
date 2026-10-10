package com.alertops.messaging;

import java.time.Duration;
import java.time.Clock;
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

import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

@Service
public class StepSchedulingService {
    private final FlowExecutionStateRepository stateRepository;
    private final EscalationRepository escalationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final int recoveryBatchSize;

    // Creates the service that persists scheduled steps and publishes wake-up events.
    public StepSchedulingService(
            FlowExecutionStateRepository stateRepository,
            EscalationRepository escalationRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${alertops.scheduler.recovery-batch-size:10000}") int recoveryBatchSize
    ) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (recoveryBatchSize < 1) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Transactional
    // Schedules a step using its configured delivery delay.
    public FlowExecutionState scheduleStep(FlowExecutionState state) {
        if (state == null) {
            throw new IllegalArgumentException("A response step is required");
        }
        Duration duration = state.getDuration();
        if (duration == null || duration.isNegative()) {
            throw new IllegalStateException("A response step requires a nonnegative wait duration");
        }

        state.setStatus(FlowExecutionStepStatus.SCHEDULED);
        state.setDueAt(clock.instant().plus(duration).truncatedTo(ChronoUnit.MICROS));
        state.setPublicationPending(true);
        FlowExecutionState saved = Objects.requireNonNull(stateRepository.save(state), "Saved response step is required");
        eventPublisher.publishEvent(toSchedule(saved));
        return saved;
    }

    @Transactional
    // Schedules a step for the supplied immediate due time.
    public FlowExecutionState scheduleStepImmediately(FlowExecutionState state, Instant dueAt) {
        if (state == null) {
            throw new IllegalArgumentException("A response step is required");
        }
        if (dueAt == null) {
            throw new IllegalArgumentException("An immediate response step requires a due time");
        }

        state.setStatus(FlowExecutionStepStatus.SCHEDULED);
        state.setDueAt(dueAt.truncatedTo(ChronoUnit.MICROS));
        state.setPublicationPending(true);
        FlowExecutionState saved = Objects.requireNonNull(stateRepository.save(state), "Saved response step is required");
        eventPublisher.publishEvent(toSchedule(saved));
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    // Loads durable step schedules that still need publication.
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
    // Marks one exact step attempt as published after broker acceptance.
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
    // Re-registers a saved step wake-up when a callback arrived too early.
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
        if (current.getStatus() != FlowExecutionStepStatus.SCHEDULED
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
        eventPublisher.publishEvent(toSchedule(saved));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    // Checks committed state without reusing entities cached before the start update.
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
        if (current.getStatus() != FlowExecutionStepStatus.SCHEDULED
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
    // Counts durable step schedules awaiting publication.
    public long countPendingSchedules() {
        return stateRepository.countPendingPublications();
    }

    // Converts a persisted step into its wake-up event payload.
    private EscalationStepSchedule toSchedule(FlowExecutionState state) {
        FlowExecutionState nonNullState = Objects.requireNonNull(state, "Response step is required");
        return new EscalationStepSchedule(
                nonNullState.getId(), nonNullState.getSendAttemptCount(), nonNullState.getDueAt());
    }

    // Checks whether an escalation can still receive delivery work.
    private boolean isActive(com.alertops.flow_execution_engine.model.Escalation escalation) {
        return escalation != null
                && escalation.getStatus() == EscalationStatus.OPEN;
    }
}
