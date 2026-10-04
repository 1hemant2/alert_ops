package com.alertops.flow_execution_engine.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.messaging.EscalationStartSchedule;
import com.alertops.flow_execution_engine.messaging.EscalationStartCancelled;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

/** Durable one-time timers for escalation starts. PostgreSQL remains the source of truth. */
@Component
public class EscalationStartScheduler {
    private static final Logger logger = LoggerFactory.getLogger(EscalationStartScheduler.class);

    private final EscalationRepository escalationRepository;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final StartFlowExecutionUseCase startFlowExecutionUseCase;
    private final FlowExecutionStateService flowExecutionStateService;
    private final EscalationStartRetryService retryService;
    private final Duration retryDelay;
    private final Map<UUID, TimerEntry> timers = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public EscalationStartScheduler(
            EscalationRepository escalationRepository,
            TaskScheduler taskScheduler,
            Clock clock,
            StartFlowExecutionUseCase startFlowExecutionUseCase,
            FlowExecutionStateService flowExecutionStateService,
            EscalationStartRetryService retryService,
            @Value("${alertops.scheduler.start-retry-delay:5s}") Duration retryDelay) {
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Scheduled-start retry delay must be positive");
        }
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startFlowExecutionUseCase = Objects.requireNonNull(startFlowExecutionUseCase, "startFlowExecutionUseCase");
        this.flowExecutionStateService = Objects.requireNonNull(
                flowExecutionStateService, "flowExecutionStateService");
        this.retryService = Objects.requireNonNull(retryService, "retryService");
        this.retryDelay = retryDelay;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduled(EscalationStartSchedule schedule) {
        if (schedule == null) {
            return;
        }
        UUID escalationId = schedule.escalationId();
        Instant scheduledStartAt = schedule.scheduledStartAt();
        schedule(escalationId, scheduledStartAt);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(EscalationStartCancelled cancellation) {
        if (cancellation == null) {
            return;
        }
        cancel(cancellation.escalationId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverScheduledStarts() {
        List<Escalation> scheduled = escalationRepository.findAllScheduled();
        if (scheduled == null || scheduled.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        for (Escalation escalation : scheduled) {
            if (escalation == null) {
                continue;
            }
            UUID escalationId = escalation.getId();
            Instant scheduledStartAt = escalation.getScheduledStartAt();
            Instant nextRetryAt = escalation.getScheduledStartNextRetryAt();
            Instant wakeAt = nextRetryAt != null && nextRetryAt.isAfter(now)
                    ? nextRetryAt
                    : scheduledStartAt;
            schedule(escalationId, wakeAt);
        }
    }

    public void schedule(UUID escalationId, Instant scheduledStartAt) {
        if (escalationId == null || scheduledStartAt == null || shuttingDown) {
            return;
        }
        // The database owns the schedule details. Memory only keeps the timer handle
        // needed to cancel or replace this wake-up while the application is running.
        TimerEntry replacement = new TimerEntry();
        TimerEntry previous = timers.put(escalationId, replacement);
        if (previous != null) {
            previous.cancel();
        }
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> handleScheduledStartTimer(escalationId, replacement), scheduledStartAt);
            replacement.future = future;
        } catch (RuntimeException e) {
            timers.remove(escalationId, replacement);
            recordTimerRegistrationFailure(escalationId, clock.instant().plus(retryDelay));
            logger.warn("Could not register scheduled escalation {}; startup recovery will retry", escalationId, e);
        }
    }

    public void cancel(UUID escalationId) {
        if (escalationId == null) {
            return;
        }
        TimerEntry entry = timers.remove(escalationId);
        if (entry != null) {
            entry.cancel();
        }
    }

    // Timer callback: validate the registration, reload database state, wait if needed, then start.
    private void handleScheduledStartTimer(UUID escalationId, TimerEntry entry) {
        if (!isCurrentTimer(escalationId, entry)) {
            return;
        }
        Instant now = clock.instant();
        Escalation scheduledEscalation = loadScheduledEscalation(escalationId, entry, now);
        if (scheduledEscalation == null) {
            return;
        }

        Instant wakeAt = nextWakeUpAt(scheduledEscalation, now);
        if (wakeAt != null) {
            retry(escalationId, entry, wakeAt);
            return;
        }
        attemptScheduledStart(escalationId, entry, now);
    }

    private boolean isCurrentTimer(UUID escalationId, TimerEntry entry) {
        return escalationId != null && entry != null
                && timers.get(escalationId) == entry && !shuttingDown;
    }

    private Escalation loadScheduledEscalation(UUID escalationId, TimerEntry entry, Instant now) {
        Escalation scheduledEscalation;
        try {
            scheduledEscalation = escalationRepository.findById(escalationId).orElse(null);
        } catch (RuntimeException e) {
            logger.warn("Could not load scheduled escalation {}; retrying", escalationId, e);
            retry(escalationId, entry, now.plus(retryDelay));
            return null;
        }
        if (!isScheduled(scheduledEscalation)) {
            timers.remove(escalationId, entry);
            return null;
        }

        return scheduledEscalation;
    }

    private boolean isScheduled(Escalation escalation) {
        return escalation != null
                && "SCHEDULED".equals(escalation.getStatus())
                && escalation.getScheduledStartAt() != null;
    }

    private Instant nextWakeUpAt(Escalation escalation, Instant now) {
        Instant nextRetryAt = escalation.getScheduledStartNextRetryAt();
        if (nextRetryAt != null && now.isBefore(nextRetryAt)) {
            return nextRetryAt;
        }
        Instant scheduledStartAt = escalation.getScheduledStartAt();
        if (scheduledStartAt != null && now.isBefore(scheduledStartAt)) {
            return scheduledStartAt;
        }
        return null;
    }

    private void attemptScheduledStart(UUID escalationId, TimerEntry entry, Instant now) {
        try {
            startFlowExecutionUseCase.executeScheduled(flowExecutionStateService, escalationId, now);
            timers.remove(escalationId, entry);
        } catch (RuntimeException e) {
            handleStartFailure(escalationId, entry, now, e);
        }
    }

    private void handleStartFailure(UUID escalationId, TimerEntry entry, Instant now, RuntimeException failure) {
        Escalation current;
        try {
            current = escalationRepository.findById(escalationId).orElse(null);
        } catch (RuntimeException reloadFailure) {
            logger.warn("Could not reload scheduled escalation {}; retry policy cannot be persisted yet",
                    escalationId, reloadFailure);
            retry(escalationId, entry, now.plus(retryDelay));
            return;
        }
        if (!isScheduled(current)) {
            timers.remove(escalationId, entry);
            return;
        }

        Optional<Instant> nextRetry;
        try {
            nextRetry = retryService.recordFailureAndPlanRetry(
                    escalationId, now.plus(retryDelay));
        } catch (RuntimeException retryPersistenceFailure) {
            logger.warn("Could not persist retry state for scheduled escalation {}; retrying temporarily",
                    escalationId, retryPersistenceFailure);
            retry(escalationId, entry, now.plus(retryDelay));
            return;
        }
        if (nextRetry.isEmpty()) {
            timers.remove(escalationId, entry);
            logger.error("Scheduled escalation {} reached its retry limit and was marked START_FAILED",
                    escalationId, failure);
            return;
        }

        Instant retryAt = nextRetry.get();
        logger.warn("Could not start scheduled escalation {}; retrying at {}", escalationId, retryAt, failure);
        retry(escalationId, entry, retryAt);
    }

    private void retry(UUID escalationId, TimerEntry entry, Instant retryAt) {
        if (escalationId == null || entry == null
                || retryAt == null || timers.get(escalationId) != entry || shuttingDown) {
            return;
        }
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> handleScheduledStartTimer(escalationId, entry), retryAt);
            entry.future = future;
        } catch (RuntimeException e) {
            recordTimerRegistrationFailure(escalationId, clock.instant().plus(retryDelay));
            logger.warn("Could not register retry for scheduled escalation {}; durable recovery will retry on startup",
                    escalationId, e);
            timers.remove(escalationId, entry);
        }
    }

    private void recordTimerRegistrationFailure(UUID escalationId, Instant retryAt) {
        if (shuttingDown) {
            return;
        }
        try {
            Optional<Instant> nextRetry = retryService.recordFailureAndPlanRetry(escalationId, retryAt);
            if (nextRetry.isEmpty()) {
                logger.error("Scheduled escalation {} reached its retry limit while registering its timer",
                        escalationId);
            }
        } catch (RuntimeException persistenceFailure) {
            logger.warn("Could not persist timer registration failure for scheduled escalation {}",
                    escalationId, persistenceFailure);
        }
    }

    @PreDestroy
    public void stop() {
        shuttingDown = true;
        timers.values().forEach(TimerEntry::cancel);
        timers.clear();
    }

    private static final class TimerEntry {
        private volatile ScheduledFuture<?> future;

        private void cancel() {
            ScheduledFuture<?> scheduledFuture = future;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }
}
