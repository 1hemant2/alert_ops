package com.alertops.messaging;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class StepTimerRegistry {
    private static final Logger logger = LoggerFactory.getLogger(StepTimerRegistry.class);

    private final Object timerLock = new Object(); // Protects the timer map.
    private final Map<UUID, TimerEntry> timers = new HashMap<>(); // Active timers kept in memory.
    private final TaskScheduler taskScheduler; // Wakes a timer when its due time arrives.
    private final Clock clock;
    private final StepSchedulingService schedulingService;
    private final MessagePublisher messagePublisher;
    private final ApplicationEventPublisher eventPublisher; // Tells recovery when a timer slot is free.
    private final Duration retryDelay;
    private final int maxActiveTimers;
    private final ThreadPoolExecutor publicationExecutor; // Sends ready work to RabbitMQ.
    private volatile boolean shuttingDown;

    public StepTimerRegistry(
            TaskScheduler taskScheduler,
            Clock clock,
            StepSchedulingService schedulingService,
            MessagePublisher messagePublisher,
            ApplicationEventPublisher eventPublisher,
            @Value("${alertops.messaging.publish-retry-delay:5s}") Duration retryDelay,
            @Value("${alertops.scheduler.max-active-timers:10000}") int maxActiveTimers,
            @Value("${alertops.scheduler.publication-threads:2}") int publicationThreads,
            @Value("${alertops.scheduler.publication-queue-capacity:100}") int publicationQueueCapacity
    ) {
        if (retryDelay.isNegative() || retryDelay.isZero()
                || maxActiveTimers < 1 || publicationThreads < 1 || publicationQueueCapacity < 1) {
            throw new IllegalArgumentException("Timer and publication limits must be positive");
        }
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.schedulingService = schedulingService;
        this.messagePublisher = messagePublisher;
        this.eventPublisher = eventPublisher;
        this.retryDelay = retryDelay;
        this.maxActiveTimers = maxActiveTimers;
        // Timer callbacks hand RabbitMQ work to this separate bounded worker pool.
        this.publicationExecutor = new ThreadPoolExecutor(
                publicationThreads,
                publicationThreads,
                0L, // No extra worker threads are kept idle.
                TimeUnit.MILLISECONDS, // Unit for the idle timeout above.
                new ArrayBlockingQueue<>(publicationQueueCapacity), // Jobs waiting for a worker.
                runnable -> {
                    // Create a named background worker for one publication job.
                    Thread thread = new Thread(runnable, "alertops-ready-publisher");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy() // A full pool makes the timer retry later.
        );
    }

    /**
     * Tries to schedule one step in memory. Returns false when the timer limit is
     * reached or the scheduler is shutting down.
     */
    public boolean tryScheduleInMemoryTimer(EscalationStepSchedule stepSchedule) {
        synchronized (timerLock) {
            // Do not add timers while the application is stopping.
            if (shuttingDown) {
                return false;
            }

            UUID stepId = stepSchedule.stepId();
            TimerEntry existingTimer = timers.get(stepId);
            // The same timer is already scheduled, so there is nothing to change.
            if (existingTimer != null && existingTimer.schedule.equals(stepSchedule)) {
                return true;
            }
            // Keep new work in PostgreSQL when the in-memory timer limit is full.
            if (existingTimer == null && timers.size() >= maxActiveTimers) {
                return false;
            }

            // Replace an older attempt or due time for this step.
            if (existingTimer != null) {
                cancelScheduledTimer(existingTimer);
            }
            TimerEntry newTimer = new TimerEntry(stepSchedule);
            timers.put(stepId, newTimer);
            // Remove the entry if Spring cannot create its timer.
            if (!scheduleTimerCallback(newTimer, stepSchedule.dueAt())) {
                timers.remove(stepId, newTimer);
                throw new IllegalStateException("Timer scheduler rejected a response step");
            }
            return true;
        }
    }

    public int activeTimerCount() {
        synchronized (timerLock) {
            return timers.size();
        }
    }

    /** Cancels the wake-up for a step that became paused or otherwise ineligible. */
    public boolean cancel(UUID stepId) {
        if (stepId == null) {
            return false;
        }
        boolean removed;
        synchronized (timerLock) {
            TimerEntry entry = timers.remove(stepId);
            removed = entry != null;
            if (entry != null) {
                cancelScheduledTimer(entry);
            }
        }
        if (removed && !shuttingDown) {
            eventPublisher.publishEvent(new TimerCapacityAvailable());
        }
        return removed;
    }

    // Runs when one timer reaches its due time.
    private void onTimer(TimerEntry entry) {
        synchronized (timerLock) {
            if (!isTimerStillValid(entry)) {
                return;
            }
            entry.future = null;
        }

        if (clock.instant().isBefore(entry.schedule.dueAt())) {
            rescheduleTimerForRetry(entry, entry.schedule.dueAt());
            return;
        }

        try {
            // Keep the timer thread free; publication work runs in the other pool.
            publicationExecutor.execute(() -> publish(entry));
        } catch (RejectedExecutionException e) {
            // The bounded publication pool is full, so try this step again later.
            logger.warn("Ready-work publisher is full; retrying step {} later", entry.schedule.stepId());
            rescheduleTimerForRetry(entry, clock.instant().plus(retryDelay));
        }
    }

    // Publishes one ready step to RabbitMQ.
    private void publish(TimerEntry entry) {
        EscalationStepSchedule schedule = entry.schedule;
        try {
            if (!isScheduleReadyToPublish(entry)) {
                removeTimerAndNotifyRecovery(entry);
                return;
            }
            if (clock.instant().isBefore(schedule.dueAt())) {
                rescheduleTimerForRetry(entry, schedule.dueAt());
                return;
            }
            messagePublisher.publishEscalationStepReady(new EscalationStepReadyMessage(
                    schedule.stepId(), schedule.sendAttemptCount(), schedule.dueAt()));
            schedulingService.markPublished(schedule);
            removeTimerAndNotifyRecovery(entry);
        } catch (RuntimeException e) {
            logger.warn("Could not publish ready work for step {}; retaining its timer for retry",
                    schedule.stepId(), e);
            rescheduleTimerForRetry(entry, clock.instant().plus(retryDelay));
        }
    }

    // Checks memory and the database before publishing.
    private boolean isScheduleReadyToPublish(TimerEntry entry) {
        if (!isTimerStillValid(entry)) {
            return false;
        }
        return schedulingService.isStepStillPendingForPublication(entry.schedule);
    }

    // Schedules the same timer again after a temporary failure.
    private void rescheduleTimerForRetry(TimerEntry entry, Instant retryTime) {
        boolean rescheduleFailed;
        synchronized (timerLock) {
            if (!isTimerStillValid(entry)) {
                return;
            }
            rescheduleFailed = !scheduleTimerCallback(entry, retryTime);
            if (rescheduleFailed) {
                timers.remove(entry.schedule.stepId(), entry);
                logger.warn("Could not reschedule timer for step {}; it will be recovered from PostgreSQL",
                        entry.schedule.stepId());
            }
        }
        if (rescheduleFailed) {
            eventPublisher.publishEvent(new TimerCapacityAvailable());
        }
    }

    // Adds one callback to Spring's timer scheduler.
    private boolean scheduleTimerCallback(TimerEntry entry, Instant dueAt) {
        try {
            ScheduledFuture<?> scheduledTimer = taskScheduler.schedule(() -> onTimer(entry), dueAt);
            if (scheduledTimer == null) {
                return false;
            }
            // Keep the future so a replaced or stopped timer can be cancelled.
            entry.future = scheduledTimer;
            return true;
        } catch (RuntimeException e) {
            logger.warn("Timer scheduler rejected step {}", entry.schedule.stepId(), e);
            return false;
        }
    }

    // Removes a timer and tells recovery that a slot is free.
    private void removeTimerAndNotifyRecovery(TimerEntry entry) {
        boolean removed;
        synchronized (timerLock) {
            removed = timers.remove(entry.schedule.stepId(), entry);
            if (removed) {
                cancelScheduledTimer(entry);
            }
        }
        if (removed && !shuttingDown) {
            eventPublisher.publishEvent(new TimerCapacityAvailable());
        }
    }

    // Confirms this callback still belongs to a live timer.
    private boolean isTimerStillValid(TimerEntry entry) {
        synchronized (timerLock) {
            return timers.get(entry.schedule.stepId()) == entry && !shuttingDown;
        }
    }

    private void cancelScheduledTimer(TimerEntry entry) {
        if (entry.future != null) {
            entry.future.cancel(false);
            entry.future = null;
        }
    }

    // Cancels all timers and stops publication workers.
    @PreDestroy
    public void stop() {
        synchronized (timerLock) {
            shuttingDown = true;
            timers.values().forEach(this::cancelScheduledTimer);
            timers.clear();
        }
        publicationExecutor.shutdown();
        try {
            if (!publicationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                publicationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            publicationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class TimerEntry {
        private final EscalationStepSchedule schedule;
        private ScheduledFuture<?> future;

        private TimerEntry(EscalationStepSchedule schedule) {
            this.schedule = schedule;
        }
    }
}
