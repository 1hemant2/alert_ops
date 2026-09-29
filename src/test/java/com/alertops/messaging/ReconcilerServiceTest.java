package com.alertops.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReconcilerServiceTest {
    private final StepSchedulingService scheduling = mock(StepSchedulingService.class);
    private final StepTimerRegistry timers = mock(StepTimerRegistry.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ReconcilerService reconciler = new ReconcilerService(
            scheduling, timers, scheduler, Duration.ofSeconds(5));

    @Test
    void healthyStartupDoesNotScheduleRecurringRecovery() {
        when(scheduling.recoverPendingSchedules()).thenReturn(List.of());

        reconciler.onStartup();

        verify(scheduling).recoverPendingSchedules();
        verifyNoInteractions(timers, scheduler);
    }

    @Test
    void fullTimerRegistryWaitsForCapacityEventAndThenRecoversPendingWork() {
        EscalationStepSchedule step = new EscalationStepSchedule(UUID.randomUUID(), 0, Instant.now());
        when(scheduling.isStepStillPendingForPublication(step)).thenReturn(true);
        when(timers.tryScheduleInMemoryTimer(step)).thenReturn(false);

        reconciler.onStepScheduled(step);
        reconciler.onStepScheduled(step);

        verifyNoInteractions(scheduler);
        verify(timers, times(2)).tryScheduleInMemoryTimer(step);

        when(timers.tryScheduleInMemoryTimer(step)).thenReturn(true);
        when(scheduling.recoverPendingSchedules()).thenReturn(List.of(step));
        reconciler.onTimerCapacityAvailable(new TimerCapacityAvailable());

        verify(timers, times(3)).tryScheduleInMemoryTimer(step);
        verifyNoInteractions(scheduler);
    }

    @Test
    void databaseValidationFailureKeepsScheduleEligibleForRecovery() {
        EscalationStepSchedule step = new EscalationStepSchedule(UUID.randomUUID(), 0, Instant.now());
        when(scheduling.isStepStillPendingForPublication(step)).thenThrow(new IllegalStateException("Database unavailable"));

        reconciler.onStepScheduled(step);

        verify(timers, never()).tryScheduleInMemoryTimer(step);
        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }
}
