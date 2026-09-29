package com.alertops.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
        this.stateRepository = stateRepository;
        this.escalationRepository = escalationRepository;
        this.eventPublisher = eventPublisher;
        if (recoveryBatchSize < 1) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Transactional
    public void schedule(FlowExecutionState state) {
        if (state.getDuration() == null || state.getDuration().isNegative()) {
            throw new IllegalStateException("A response step requires a nonnegative wait duration");
        }

        state.setExecutionState("ACTIVE");
        state.setDueAt(Instant.now().plus(state.getDuration()).truncatedTo(ChronoUnit.MICROS));
        state.setPublicationPending(true);
        FlowExecutionState saved = stateRepository.save(state);
        // The listener adds the timer only after this database save commits.
        eventPublisher.publishEvent(toSchedule(saved));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<EscalationStepSchedule> recoverPendingSchedules() {
        List<FlowExecutionState> pending = stateRepository.findPendingPublications(
                PageRequest.of(0, recoveryBatchSize));
        return pending.stream().map(this::toSchedule).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(EscalationStepSchedule step) {
        // A late confirmation for an older attempt must not clear a newly scheduled retry.
        stateRepository.markPublished(step.stepId(), step.sendAttemptCount(), step.dueAt());
    }

    @Transactional
    public void rescheduleStepAtDueTime(FlowExecutionState state) {
        if (state.getDueAt() == null || state.getId() == null) {
            return;
        }
        FlowExecutionState current = stateRepository.findById(state.getId()).orElse(null);
        if (current == null
                || !"ACTIVE".equals(current.getExecutionState())
                || !"NOT_SENT".equals(current.getNotificationState())
                || current.getSendAttemptCount() != state.getSendAttemptCount()
                || !state.getDueAt().equals(current.getDueAt())
                || current.getProcessId() == null
                || !escalationRepository.findById(current.getProcessId())
                        .map(escalation -> "RUNNING".equals(escalation.getStatus())).orElse(false)) {
            return;
        }

        current.setPublicationPending(true);
        FlowExecutionState saved = stateRepository.save(current);
        // Re-register the saved timer after this transaction commits.
        eventPublisher.publishEvent(toSchedule(saved));
    }

    // Checks that this exact step attempt is still pending before scheduling its timer.
    @Transactional(readOnly = true)
    public boolean isStepStillPendingForPublication(EscalationStepSchedule schedule) {
        FlowExecutionState current = stateRepository.findById(schedule.stepId()).orElse(null);
        if (current == null
                || !"ACTIVE".equals(current.getExecutionState())
                || !"NOT_SENT".equals(current.getNotificationState())
                || !current.isPublicationPending()
                || current.getSendAttemptCount() != schedule.sendAttemptCount()
                || !schedule.dueAt().equals(current.getDueAt())
                || current.getProcessId() == null) {
            return false;
        }
        return escalationRepository.findById(current.getProcessId())
                .map(escalation -> "RUNNING".equals(escalation.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public long countPendingSchedules() {
        return stateRepository.countPendingPublications();
    }

    private EscalationStepSchedule toSchedule(FlowExecutionState state) {
        return new EscalationStepSchedule(state.getId(), state.getSendAttemptCount(), state.getDueAt());
    }
}
