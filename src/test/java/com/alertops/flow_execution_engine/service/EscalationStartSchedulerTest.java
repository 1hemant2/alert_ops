package com.alertops.flow_execution_engine.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

class EscalationStartSchedulerTest {
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final StartFlowExecutionUseCase start = mock(StartFlowExecutionUseCase.class);
    private final FlowExecutionStateService stateService = mock(FlowExecutionStateService.class);
    private final EscalationStartRetryService retryService = mock(EscalationStartRetryService.class);
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final EscalationStartScheduler scheduler = new EscalationStartScheduler(
            escalations, taskScheduler, Clock.fixed(now, ZoneOffset.UTC), start, stateService,
            retryService, Duration.ofSeconds(5));

    @Test
    void startupRecoversPersistedSchedules() {
        Escalation escalation = new Escalation();
        UUID id = UUID.randomUUID();
        escalation.setId(id);
        escalation.setScheduledStartAt(now.plusSeconds(60));
        when(escalations.findAllScheduled()).thenReturn(List.of(escalation));
        when(taskScheduler.schedule(any(Runnable.class), eq(escalation.getScheduledStartAt())))
                .thenReturn(mock(ScheduledFuture.class));

        scheduler.recoverScheduledStarts();

        verify(taskScheduler).schedule(any(Runnable.class), eq(escalation.getScheduledStartAt()));
    }

    @Test
    void startupUsesPersistedRetryTimeWhenOneExists() {
        Escalation escalation = new Escalation();
        UUID id = UUID.randomUUID();
        Instant retryAt = now.plusSeconds(30);
        escalation.setId(id);
        escalation.setScheduledStartAt(now.minusSeconds(60));
        escalation.setScheduledStartNextRetryAt(retryAt);
        when(escalations.findAllScheduled()).thenReturn(List.of(escalation));
        when(taskScheduler.schedule(any(Runnable.class), eq(retryAt)))
                .thenReturn(mock(ScheduledFuture.class));

        scheduler.recoverScheduledStarts();

        verify(taskScheduler).schedule(any(Runnable.class), eq(retryAt));
    }

    @Test
    void duplicateTimerCallbacksCanOnlyInvokeTheStartUseCaseOncePerRegisteredTimer() {
        UUID id = UUID.randomUUID();
        Escalation escalation = new Escalation();
        escalation.setId(id);
        escalation.setStatus(EscalationStatus.SCHEDULED);
        escalation.setScheduledStartAt(now);
        when(escalations.findById(id)).thenReturn(Optional.of(escalation));
        when(taskScheduler.schedule(any(Runnable.class), eq(now))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return mock(ScheduledFuture.class);
        });
        scheduler.schedule(id, now);

        verify(start).executeScheduled(stateService, id, now);
    }

    @Test
    void staleTimerAfterCancellationDoesNotStartEscalation() {
        UUID id = UUID.randomUUID();
        Escalation cancelled = new Escalation();
        cancelled.setId(id);
        cancelled.setStatus(EscalationStatus.CANCELLED);
        cancelled.setScheduledStartAt(now);
        when(escalations.findById(id)).thenReturn(Optional.of(cancelled));
        AtomicReference<Runnable> callback = new AtomicReference<>();
        when(taskScheduler.schedule(any(Runnable.class), eq(now))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });

        scheduler.schedule(id, now);
        callback.get().run();

        verifyNoInteractions(start);
    }

    @Test
    void failedStartUsesPersistedRetryTimeForTheNextTimer() {
        UUID id = UUID.randomUUID();
        Instant retryAt = now.plusSeconds(5);
        Escalation escalation = new Escalation();
        escalation.setId(id);
        escalation.setStatus(EscalationStatus.SCHEDULED);
        escalation.setScheduledStartAt(now);
        when(escalations.findById(id)).thenReturn(Optional.of(escalation));
        doThrow(new IllegalStateException("temporary failure"))
                .when(start).executeScheduled(stateService, id, now);
        when(retryService.recordFailureAndPlanRetry(eq(id), eq(retryAt), any(String.class)))
                .thenReturn(Optional.of(retryAt));
        AtomicReference<Runnable> callback = new AtomicReference<>();
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });

        scheduler.schedule(id, now);
        callback.get().run();

        verify(taskScheduler).schedule(any(Runnable.class), eq(retryAt));
        verify(retryService).recordFailureAndPlanRetry(eq(id), eq(retryAt), any(String.class));
    }

    @Test
    void rejectedTimerRegistrationPersistsARecoveryAttempt() {
        UUID id = UUID.randomUUID();
        when(taskScheduler.schedule(any(Runnable.class), eq(now)))
                .thenThrow(new IllegalStateException("scheduler unavailable"));

        scheduler.schedule(id, now);

        verify(retryService).recordFailureAndPlanRetry(
                eq(id), eq(now.plusSeconds(5)), any(String.class));
    }

    @Test
    void nullInputsAreIgnoredWithoutTouchingTheTimerOrRepository() {
        scheduler.onScheduled(null);
        scheduler.onCancelled(null);
        scheduler.schedule(null, now);
        scheduler.schedule(UUID.randomUUID(), null);
        scheduler.cancel(null);

        verifyNoInteractions(escalations, taskScheduler, start);
    }
}
