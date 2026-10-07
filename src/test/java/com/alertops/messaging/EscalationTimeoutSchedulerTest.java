package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class EscalationTimeoutSchedulerTest {
    private static final UUID ESCALATION_ID = UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final UUID STEP_ID = UUID.fromString("74000000-0000-0000-0000-000000000002");
    private static final Instant DUE_AT = Instant.parse("2026-10-07T12:00:00Z");

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final EscalationTimeoutService timeoutService = mock(EscalationTimeoutService.class);
    @SuppressWarnings("rawtypes")
    private final ScheduledFuture future = mock(ScheduledFuture.class);
    private final EscalationTimeoutScheduler timeoutScheduler =
            new EscalationTimeoutScheduler(
                    scheduler, Clock.fixed(DUE_AT, ZoneOffset.UTC), timeoutService,
                    Duration.ofSeconds(5), 10);

    @Test
    // Verifies that startup recovery restores saved timeout wake-ups.
    void startupRecoveryRestoresSavedTimeoutWakeups() {
        EscalationTimeoutSchedule schedule = timeoutSchedule();
        when(timeoutService.loadPendingTimeouts()).thenReturn(List.of(schedule));
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future);

        timeoutScheduler.recoverTimeoutWakeUpsOnStartup();

        assertEquals(1, timeoutScheduler.activeTimeoutWakeUpCount());
        assertEquals(true, timeoutScheduler.isTimeoutRecoveryComplete());
    }

    @Test
    // Verifies that cancellation removes the matching wake-up handle.
    void cancellationRemovesTheWakeupHandle() {
        EscalationTimeoutSchedule schedule = timeoutSchedule();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future);
        timeoutScheduler.onTimeoutScheduled(schedule);

        timeoutScheduler.onTimeoutCancelled(new EscalationTimeoutCancellation(
                ESCALATION_ID, EscalationTimeoutType.RESOLUTION));

        assertEquals(0, timeoutScheduler.activeTimeoutWakeUpCount());
        verify(future).cancel(false);
    }

    @Test
    // Verifies that a due wake-up applies its timeout and is removed.
    void dueWakeupAppliesTimeoutAndRemovesHandle() {
        EscalationTimeoutSchedule schedule = timeoutSchedule();
        AtomicReference<Runnable> callback = new AtomicReference<>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return future;
        });
        when(timeoutService.loadPendingTimeouts()).thenReturn(List.of());

        timeoutScheduler.onTimeoutScheduled(schedule);
        callback.get().run();

        verify(timeoutService).handleTimeout(schedule);
        assertEquals(0, timeoutScheduler.activeTimeoutWakeUpCount());
    }

    @Test
    // Verifies that a failed expiry remains registered for retry.
    void timeoutFailureRetainsTheWakeupForRetry() {
        EscalationTimeoutSchedule schedule = timeoutSchedule();
        AtomicReference<Runnable> callback = new AtomicReference<>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return future;
        });
        doThrow(new IllegalStateException("database unavailable"))
                .when(timeoutService).handleTimeout(schedule);

        timeoutScheduler.onTimeoutScheduled(schedule);
        callback.get().run();

        verify(timeoutService).handleTimeout(schedule);
        assertEquals(1, timeoutScheduler.activeTimeoutWakeUpCount());
    }

    // Builds a resolution timeout schedule for scheduler scenarios.
    private EscalationTimeoutSchedule timeoutSchedule() {
        return new EscalationTimeoutSchedule(
                ESCALATION_ID, STEP_ID, EscalationTimeoutType.RESOLUTION, DUE_AT);
    }
}
