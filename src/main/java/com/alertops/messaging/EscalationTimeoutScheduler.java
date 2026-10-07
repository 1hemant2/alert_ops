package com.alertops.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Owns only in-memory timeout wake-up handles. It does not change escalation
 * state; {@link EscalationTimeoutService} reloads PostgreSQL state and applies
 * the outcome when a handle fires. It receives committed schedule/cancel
 * events, recovers saved waits at startup, and retries registration or expiry
 * when the scheduler is temporarily unavailable.
 */
@Component
public class EscalationTimeoutScheduler {
    private static final Logger logger = LoggerFactory.getLogger(EscalationTimeoutScheduler.class);

    private final Object timerLock = new Object();
    private final Map<TimeoutKey, TimerEntry> timers = new HashMap<>();
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final EscalationTimeoutService timeoutService;
    private final Duration retryDelay;
    private final int maxActiveTimers;
    private final AtomicBoolean retryScheduled = new AtomicBoolean();
    private volatile boolean shuttingDown;
    private boolean recoveryComplete;

    // Creates the scheduler that owns in-memory timeout wake-up handles.
    public EscalationTimeoutScheduler(
            TaskScheduler taskScheduler,
            Clock clock,
            EscalationTimeoutService timeoutService,
            @Value("${alertops.messaging.publish-retry-delay:5s}") Duration retryDelay,
            @Value("${alertops.scheduler.max-active-deadline-timers:10000}") int maxActiveTimers) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeoutService = Objects.requireNonNull(timeoutService, "timeoutService");
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero() || maxActiveTimers < 1) {
            throw new IllegalArgumentException("Timeout timer limits must be positive");
        }
        this.retryDelay = retryDelay;
        this.maxActiveTimers = maxActiveTimers;
    }

    @EventListener
    // Registers or replaces a timeout wake-up after durable state is saved.
    public synchronized void onTimeoutScheduled(EscalationTimeoutSchedule schedule) {
        if (!registerTimeoutWakeUp(schedule)) {
            markTimeoutRecoveryRequired();
            scheduleTimeoutRecoveryRetry();
        }
    }

    @EventListener
    // Removes the matching timeout wake-up after durable cancellation.
    public synchronized void onTimeoutCancelled(EscalationTimeoutCancellation cancellation) {
        if (cancellation == null || cancellation.escalationId() == null || cancellation.type() == null) {
            return;
        }
        TimerEntry entry;
        synchronized (timerLock) {
            entry = timers.remove(new TimeoutKey(cancellation.escalationId(), cancellation.type()));
            if (entry != null) {
                cancelTimeoutWakeUp(entry);
            }
        }
        if (entry != null && !shuttingDown) {
            markTimeoutRecoveryRequired();
            recoverMissingTimeoutWakeUps();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    // Recovers saved timeout wake-ups when the application is ready.
    public synchronized void recoverTimeoutWakeUpsOnStartup() {
        recoveryComplete = false;
        recoverMissingTimeoutWakeUps();
    }

    // Loads missing timeout wake-ups until all saved waits are registered.
    public synchronized void recoverMissingTimeoutWakeUps() {
        if (recoveryComplete || shuttingDown) {
            return;
        }
        try {
            List<EscalationTimeoutSchedule> pending = timeoutService.loadPendingTimeouts();
            if (pending == null || pending.isEmpty()) {
                recoveryComplete = true;
                return;
            }
            for (EscalationTimeoutSchedule schedule : pending) {
                if (!registerTimeoutWakeUp(schedule)) {
                    scheduleTimeoutRecoveryRetry();
                    return;
                }
            }
            recoveryComplete = true;
        } catch (RuntimeException e) {
            logger.warn("Could not recover escalation timeouts; recovery will be retried", e);
            markTimeoutRecoveryRequired();
            scheduleTimeoutRecoveryRetry();
        }
    }

    // Reports whether all currently loaded timeout wake-ups were registered.
    public synchronized boolean isTimeoutRecoveryComplete() {
        return recoveryComplete;
    }

    // Reports the number of active in-memory timeout wake-up handles.
    public int activeTimeoutWakeUpCount() {
        synchronized (timerLock) {
            return timers.size();
        }
    }

    @PreDestroy
    // Cancels all timeout wake-ups during application shutdown.
    public void cancelAllTimeoutWakeUps() {
        shuttingDown = true;
        synchronized (timerLock) {
            timers.values().forEach(this::cancelTimeoutWakeUp);
            timers.clear();
        }
    }

    // Registers one timeout wake-up while respecting the in-memory capacity.
    private boolean registerTimeoutWakeUp(EscalationTimeoutSchedule schedule) {
        if (!isValidTimeoutSchedule(schedule) || shuttingDown) {
            return true;
        }
        TimeoutKey key = new TimeoutKey(schedule.escalationId(), schedule.type());
        synchronized (timerLock) {
            TimerEntry existing = timers.get(key);
            if (existing != null && existing.schedule.equals(schedule)) {
                return true;
            }
            if (existing == null && timers.size() >= maxActiveTimers) {
                logger.warn("Timeout timer capacity is full; {} remains recoverable in PostgreSQL", key);
                return false;
            }
            if (existing != null) {
                cancelTimeoutWakeUp(existing);
            }
            TimerEntry entry = new TimerEntry(schedule);
            timers.put(key, entry);
            if (!scheduleWakeUpCallback(entry, schedule.dueAt())) {
                timers.remove(key, entry);
                return false;
            }
            return true;
        }
    }

    // Handles a timeout wake-up and delegates the durable outcome.
    private void handleTimeoutWakeUp(TimerEntry entry) {
        synchronized (timerLock) {
            if (!isCurrentTimeoutWakeUp(entry)) {
                return;
            }
            entry.future = null;
        }

        if (clock.instant().isBefore(entry.schedule.dueAt())) {
            rescheduleTimeoutWakeUp(entry, entry.schedule.dueAt());
            return;
        }

        try {
            timeoutService.handleTimeout(entry.schedule);
            removeTimeoutWakeUp(entry);
        } catch (RuntimeException e) {
            logger.warn("Could not process escalation timeout {}; retaining it for retry",
                    entry.schedule, e);
            rescheduleTimeoutWakeUp(entry, clock.instant().plus(retryDelay));
        }
    }

    // Reschedules the same wake-up after an early callback or transient failure.
    private void rescheduleTimeoutWakeUp(TimerEntry entry, Instant dueAt) {
        boolean failed;
        synchronized (timerLock) {
            if (!isCurrentTimeoutWakeUp(entry)) {
                return;
            }
            failed = !scheduleWakeUpCallback(entry, dueAt);
            if (failed) {
                timers.remove(new TimeoutKey(entry.schedule.escalationId(), entry.schedule.type()), entry);
            }
        }
        if (failed) {
            markTimeoutRecoveryRequired();
            scheduleTimeoutRecoveryRetry();
        }
    }

    // Removes a completed timeout wake-up and triggers recovery for any gap.
    private void removeTimeoutWakeUp(TimerEntry entry) {
        boolean removed;
        synchronized (timerLock) {
            removed = timers.remove(
                    new TimeoutKey(entry.schedule.escalationId(), entry.schedule.type()), entry);
            if (removed) {
                cancelTimeoutWakeUp(entry);
            }
        }
        if (removed && !shuttingDown) {
            markTimeoutRecoveryRequired();
            recoverMissingTimeoutWakeUps();
        }
    }

    // Registers the scheduler callback for one timeout wake-up.
    private boolean scheduleWakeUpCallback(TimerEntry entry, Instant dueAt) {
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(() -> handleTimeoutWakeUp(entry), dueAt);
            if (future == null) {
                return false;
            }
            entry.future = future;
            return true;
        } catch (RuntimeException e) {
            logger.warn("Could not register escalation timeout {}", entry.schedule, e);
            return false;
        }
    }

    // Confirms that a callback still owns the current wake-up slot.
    private boolean isCurrentTimeoutWakeUp(TimerEntry entry) {
        synchronized (timerLock) {
            return timers.get(new TimeoutKey(entry.schedule.escalationId(), entry.schedule.type())) == entry
                    && !shuttingDown;
        }
    }

    // Cancels the scheduler handle owned by one timeout wake-up.
    private void cancelTimeoutWakeUp(TimerEntry entry) {
        if (entry.future != null) {
            entry.future.cancel(false);
            entry.future = null;
        }
    }

    // Marks the scheduler for another durable timeout recovery pass.
    private synchronized void markTimeoutRecoveryRequired() {
        recoveryComplete = false;
    }

    // Schedules a bounded retry when a timeout wake-up cannot be recovered now.
    private void scheduleTimeoutRecoveryRetry() {
        if (retryScheduled.compareAndSet(false, true)) {
            try {
                taskScheduler.schedule(() -> {
                    retryScheduled.set(false);
                    recoverMissingTimeoutWakeUps();
                }, clock.instant().plus(retryDelay));
            } catch (RuntimeException e) {
                retryScheduled.set(false);
                logger.warn("Could not schedule timeout recovery; saved waits remain recoverable on restart", e);
            }
        }
    }

    // Checks whether a timeout schedule contains all required values.
    private boolean isValidTimeoutSchedule(EscalationTimeoutSchedule schedule) {
        return schedule != null
                && schedule.escalationId() != null
                && schedule.executionStepId() != null
                && schedule.type() != null
                && schedule.dueAt() != null;
    }

    private record TimeoutKey(UUID escalationId, EscalationTimeoutType type) {
    }

    private static final class TimerEntry {
        private final EscalationTimeoutSchedule schedule;
        private ScheduledFuture<?> future;

        // Creates one in-memory handle for a saved timeout.
        private TimerEntry(EscalationTimeoutSchedule schedule) {
            this.schedule = schedule;
        }
    }
}
