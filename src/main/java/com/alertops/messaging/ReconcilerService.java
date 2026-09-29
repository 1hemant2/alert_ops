package com.alertops.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReconcilerService {
    private static final Logger logger = LoggerFactory.getLogger(ReconcilerService.class);

    private final StepSchedulingService schedulingService;
    private final StepTimerRegistry timerRegistry;
    private final TaskScheduler taskScheduler;
    private final Duration retryDelay;
    private final AtomicBoolean retryScheduled = new AtomicBoolean();
    private boolean recoveryComplete;

    public ReconcilerService(
            StepSchedulingService schedulingService,
            StepTimerRegistry timerRegistry,
            TaskScheduler taskScheduler,
            @Value("${alertops.messaging.publish-retry-delay:5s}") Duration retryDelay
    ) {
        this.schedulingService = schedulingService;
        this.timerRegistry = timerRegistry;
        this.taskScheduler = taskScheduler;
        this.retryDelay = retryDelay;
        if (retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("Publication retry delay must be positive");
        }
    }

    // After the step save commits, put its schedule into the in-memory timer.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onStepScheduled(EscalationStepSchedule step) {
        if (!tryRegisterPendingTimer(step)) {
            markRecoveryNeeded();
        }
    }

    @EventListener
    public void onTimerCapacityAvailable(TimerCapacityAvailable event) {
        reconcileOnce();
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onStartup() {
        recoveryComplete = false;
        reconcileOnce();
    }

    public synchronized void reconcileOnce() {
        if (recoveryComplete) {
            return;
        }
        try {
            List<EscalationStepSchedule> pending = schedulingService.recoverPendingSchedules();
            if (pending.isEmpty()) {
                recoveryComplete = true;
                return;
            }
            for (EscalationStepSchedule step : pending) {
                if (!tryRegisterPendingTimer(step)) {
                    return;
                }
            }
            recoveryComplete = true;
        } catch (RuntimeException e) {
            logger.warn("Could not recover pending step publications; recovery will be retried", e);
            markRecoveryNeeded();
            retryLater();
        }
    }

    private synchronized void markRecoveryNeeded() {
        recoveryComplete = false;
    }

    public synchronized boolean isRecoveryComplete() {
        return recoveryComplete;
    }

    private boolean tryRegisterPendingTimer(EscalationStepSchedule step) {
        try {
            if (!schedulingService.isStepStillPendingForPublication(step)) {
                return true;
            }
            if (!timerRegistry.tryScheduleInMemoryTimer(step)) {
                logger.warn("Timer capacity is full; step {} remains pending in PostgreSQL", step.stepId());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // The saved publicationPending flag keeps this schedule recoverable.
            logger.warn("Could not register response step {}; registration will be retried", step.stepId(), e);
            retryLater();
            return false;
        }
    }

    private void retryLater() {
        // Only failed publication or a remaining recovery backlog creates a timer.
        // A healthy, idle application makes no recurring recovery queries.
        if (retryScheduled.compareAndSet(false, true)) {
            try {
                taskScheduler.schedule(() -> {
                    retryScheduled.set(false);
                    reconcileOnce();
                }, Instant.now().plus(retryDelay));
            } catch (RuntimeException e) {
                retryScheduled.set(false);
                logger.warn("Could not schedule publication recovery; saved steps remain recoverable on restart", e);
            }
        }
    }
}
