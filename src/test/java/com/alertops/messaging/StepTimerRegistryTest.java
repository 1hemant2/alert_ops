package com.alertops.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StepTimerRegistryTest {
    private static final Instant START = Instant.parse("2026-09-29T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final StepSchedulingService scheduling = mock(StepSchedulingService.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BlockingQueue<ScheduledCall> calls = new LinkedBlockingQueue<>();
    private StepTimerRegistry registry;

    StepTimerRegistryTest() {
        doAnswer(invocation -> {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            calls.add(new ScheduledCall(invocation.getArgument(0), invocation.getArgument(1), future));
            return future;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    @Test
    void schedulesIndependentDeadlinesAndDeduplicatesAnIdenticalRequest() throws Exception {
        registry = registry(10);
        EscalationStepSchedule longWait = step(UUID.randomUUID(), 0, START.plus(Duration.ofHours(1)));
        EscalationStepSchedule shortWait = step(UUID.randomUUID(), 0, START.plus(Duration.ofSeconds(30)));

        assertThat(registry.tryScheduleInMemoryTimer(longWait)).isTrue();
        assertThat(registry.tryScheduleInMemoryTimer(shortWait)).isTrue();
        assertThat(registry.tryScheduleInMemoryTimer(shortWait)).isTrue();

        ScheduledCall first = takeCall();
        ScheduledCall second = takeCall();
        assertThat(first.dueAt()).isEqualTo(longWait.dueAt());
        assertThat(second.dueAt()).isEqualTo(shortWait.dueAt());
        assertThat(calls).isEmpty();
        assertThat(registry.activeTimerCount()).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalRegistrationsInstallOnlyOneTimer() throws Exception {
        registry = registry(10);
        EscalationStepSchedule step = step(UUID.randomUUID(), 0, START.plusSeconds(30));
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            var registrations = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return registry.tryScheduleInMemoryTimer(step);
                    }, workers))
                    .toList();

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var registration : registrations) {
                assertThat(registration.get(3, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            workers.shutdownNow();
        }

        assertThat(registry.activeTimerCount()).isEqualTo(1);
        assertThat(calls).hasSize(1);
    }

    @Test
    void shorterWaitCanPublishBeforeTheLongerWaitRegisteredFirst() throws Exception {
        registry = registry(10);
        EscalationStepSchedule longWait = step(UUID.randomUUID(), 0, START.plus(Duration.ofHours(1)));
        EscalationStepSchedule shortWait = step(UUID.randomUUID(), 0, START.plus(Duration.ofSeconds(30)));
        when(scheduling.isStepStillPendingForPublication(longWait)).thenReturn(true);
        when(scheduling.isStepStillPendingForPublication(shortWait)).thenReturn(true);
        CountDownLatch shortPublished = new CountDownLatch(1);
        CountDownLatch longPublished = new CountDownLatch(1);
        doAnswer(invocation -> {
            EscalationStepReadyMessage delivery = invocation.getArgument(0);
            if (delivery.stepId().equals(shortWait.stepId())) {
                shortPublished.countDown();
            } else if (delivery.stepId().equals(longWait.stepId())) {
                longPublished.countDown();
            }
            return null;
        }).when(publisher).publishEscalationStepReady(any(EscalationStepReadyMessage.class));

        assertThat(registry.tryScheduleInMemoryTimer(longWait)).isTrue();
        assertThat(registry.tryScheduleInMemoryTimer(shortWait)).isTrue();
        ScheduledCall longCall = takeCall();
        ScheduledCall shortCall = takeCall();

        clock.set(shortWait.dueAt());
        shortCall.runnable().run();
        assertThat(shortPublished.await(3, TimeUnit.SECONDS)).isTrue();
        verify(publisher, never()).publishEscalationStepReady(new EscalationStepReadyMessage(
                longWait.stepId(), longWait.sendAttemptCount(), longWait.dueAt()));

        clock.set(longWait.dueAt());
        longCall.runnable().run();
        assertThat(longPublished.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void replacingAnAttemptCancelsTheOldCallbackAndKeepsOnlyTheNewTimer() throws Exception {
        registry = registry(10);
        UUID stepId = UUID.randomUUID();
        EscalationStepSchedule oldAttempt = step(stepId, 0, START.plusSeconds(30));
        EscalationStepSchedule newAttempt = step(stepId, 1, START.plusSeconds(60));

        assertThat(registry.tryScheduleInMemoryTimer(oldAttempt)).isTrue();
        ScheduledCall oldCall = takeCall();
        assertThat(registry.tryScheduleInMemoryTimer(newAttempt)).isTrue();
        ScheduledCall newCall = takeCall();

        verify(oldCall.future()).cancel(false);
        oldCall.runnable().run();
        verifyNoInteractions(publisher);
        assertThat(newCall.dueAt()).isEqualTo(newAttempt.dueAt());
        assertThat(registry.activeTimerCount()).isEqualTo(1);
    }

    @Test
    void cancellationRemovesAndCancelsAStillScheduledWakeUp() throws Exception {
        registry = registry(10);
        UUID stepId = UUID.randomUUID();
        EscalationStepSchedule scheduled = step(stepId, 0, START.plusSeconds(30));

        assertThat(registry.tryScheduleInMemoryTimer(scheduled)).isTrue();
        ScheduledCall call = takeCall();

        assertThat(registry.cancel(stepId)).isTrue();
        assertThat(registry.cancel(stepId)).isFalse();
        verify(call.future()).cancel(false);
        assertThat(registry.activeTimerCount()).isZero();
    }

    @Test
    void overdueTimerPublishesReadyWorkAndFreesItsSlot() throws Exception {
        registry = registry(1);
        EscalationStepSchedule overdue = step(UUID.randomUUID(), 3, START.minusSeconds(1));
        CountDownLatch published = new CountDownLatch(1);
        CountDownLatch slotFreed = listenForSlotFreed();
        when(scheduling.isStepStillPendingForPublication(overdue)).thenReturn(true);
        doAnswer(invocation -> {
            published.countDown();
            return null;
        }).when(publisher).publishEscalationStepReady(new EscalationStepReadyMessage(overdue.stepId(), overdue.sendAttemptCount(), overdue.dueAt()));

        assertThat(registry.tryScheduleInMemoryTimer(overdue)).isTrue();
        takeCall().runnable().run();

        assertThat(published.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(slotFreed.await(3, TimeUnit.SECONDS)).isTrue();
        verify(scheduling).markPublished(overdue);
        assertThat(registry.activeTimerCount()).isZero();
    }

    @Test
    void earlyCallbackIsRearmedUntilItsSavedDueTime() throws Exception {
        registry = registry(10);
        EscalationStepSchedule future = step(UUID.randomUUID(), 0, START.plusSeconds(30));
        when(scheduling.isStepStillPendingForPublication(future)).thenReturn(true);

        assertThat(registry.tryScheduleInMemoryTimer(future)).isTrue();
        takeCall().runnable().run();
        assertThat(registry.activeTimerCount()).isEqualTo(1);
        verifyNoInteractions(publisher);

        ScheduledCall rearmed = takeCall();
        assertThat(rearmed.dueAt()).isEqualTo(future.dueAt());
        clock.set(future.dueAt());
        CountDownLatch published = new CountDownLatch(1);
        CountDownLatch slotFreed = listenForSlotFreed();
        doAnswer(invocation -> {
            published.countDown();
            return null;
        }).when(publisher).publishEscalationStepReady(new EscalationStepReadyMessage(future.stepId(), future.sendAttemptCount(), future.dueAt()));

        rearmed.runnable().run();
        assertThat(published.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(slotFreed.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void fullTimerRegistryLeavesNewScheduleForRecoveryUntilASlotFrees() throws Exception {
        registry = registry(1);
        EscalationStepSchedule first = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        EscalationStepSchedule waiting = step(UUID.randomUUID(), 0, START.plusSeconds(10));
        CountDownLatch slotFreed = listenForSlotFreed();
        when(scheduling.isStepStillPendingForPublication(first)).thenReturn(true);

        assertThat(registry.tryScheduleInMemoryTimer(first)).isTrue();
        assertThat(registry.tryScheduleInMemoryTimer(waiting)).isFalse();
        takeCall().runnable().run();
        assertThat(slotFreed.await(3, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.tryScheduleInMemoryTimer(waiting)).isTrue();
        assertThat(registry.activeTimerCount()).isEqualTo(1);
    }

    @Test
    void publisherFailureRetainsPendingTimerAndSchedulesAnotherAttempt() throws Exception {
        registry = registry(10);
        EscalationStepSchedule overdue = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        when(scheduling.isStepStillPendingForPublication(overdue)).thenReturn(true);
        doThrow(new IllegalStateException("broker down"))
                .when(publisher).publishEscalationStepReady(new EscalationStepReadyMessage(overdue.stepId(), 0, overdue.dueAt()));

        assertThat(registry.tryScheduleInMemoryTimer(overdue)).isTrue();
        takeCall().runnable().run();

        ScheduledCall retry = takeCall();
        assertThat(retry.dueAt()).isEqualTo(START.plusSeconds(5));
        assertThat(registry.activeTimerCount()).isEqualTo(1);
        verify(scheduling, never()).markPublished(any());
    }

    @Test
    void readyMessageAcceptedBeforeDatabaseConfirmationFailureMayBeRepublished() throws Exception {
        registry = registry(10);
        EscalationStepSchedule overdue = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        AtomicInteger confirmations = new AtomicInteger();
        CountDownLatch publishedTwice = new CountDownLatch(2);
        CountDownLatch slotFreed = listenForSlotFreed();
        when(scheduling.isStepStillPendingForPublication(overdue)).thenReturn(true);
        doAnswer(invocation -> {
            publishedTwice.countDown();
            return null;
        }).when(publisher).publishEscalationStepReady(new EscalationStepReadyMessage(overdue.stepId(), 0, overdue.dueAt()));
        doAnswer(invocation -> {
            if (confirmations.getAndIncrement() == 0) {
                throw new IllegalStateException("Database unavailable after broker confirmation");
            }
            return null;
        }).when(scheduling).markPublished(overdue);

        assertThat(registry.tryScheduleInMemoryTimer(overdue)).isTrue();
        takeCall().runnable().run();
        ScheduledCall retry = takeCall();
        assertThat(retry.dueAt()).isEqualTo(START.plusSeconds(5));

        clock.set(retry.dueAt());
        retry.runnable().run();
        assertThat(publishedTwice.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(slotFreed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(confirmations.get()).isEqualTo(2);
        assertThat(registry.activeTimerCount()).isZero();
    }

    @Test
    void saturatedPublicationExecutorRearmsTheDueStep() throws Exception {
        registry = registry(3);
        EscalationStepSchedule first = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        EscalationStepSchedule second = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        EscalationStepSchedule third = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        when(scheduling.isStepStillPendingForPublication(any(EscalationStepSchedule.class))).thenReturn(true);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstTwoPublished = new CountDownLatch(2);
        CountDownLatch published = new CountDownLatch(3);
        CountDownLatch slotsFreed = listenForSlotFreed(3);
        doAnswer(invocation -> {
            EscalationStepReadyMessage delivery = invocation.getArgument(0);
            if (delivery.stepId().equals(first.stepId())) {
                firstEntered.countDown();
                releaseFirst.await(3, TimeUnit.SECONDS);
            }
            if (!delivery.stepId().equals(third.stepId())) {
                firstTwoPublished.countDown();
            }
            published.countDown();
            return null;
        }).when(publisher).publishEscalationStepReady(any(EscalationStepReadyMessage.class));

        assertThat(registry.tryScheduleInMemoryTimer(first)).isTrue();
        assertThat(registry.tryScheduleInMemoryTimer(second)).isTrue();
        assertThat(registry.tryScheduleInMemoryTimer(third)).isTrue();
        ScheduledCall firstCall = takeCall();
        ScheduledCall secondCall = takeCall();
        ScheduledCall thirdCall = takeCall();
        firstCall.runnable().run();
        assertThat(firstEntered.await(3, TimeUnit.SECONDS)).isTrue();
        secondCall.runnable().run();
        thirdCall.runnable().run();

        ScheduledCall retry = takeCall();
        assertThat(retry.dueAt()).isEqualTo(START.plusSeconds(5));
        releaseFirst.countDown();
        assertThat(firstTwoPublished.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(slotsFreed.getCount()).isLessThan(3);

        clock.set(retry.dueAt());
        retry.runnable().run();
        assertThat(published.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(slotsFreed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.activeTimerCount()).isZero();
    }

    @Test
    void stoppingCancelsFutureTimersAndLeavesNoInMemoryEntries() throws Exception {
        registry = registry(10);
        EscalationStepSchedule future = step(UUID.randomUUID(), 0, START.plusSeconds(30));

        assertThat(registry.tryScheduleInMemoryTimer(future)).isTrue();
        ScheduledCall call = takeCall();
        registry.stop();
        call.runnable().run();

        verify(call.future()).cancel(false);
        verifyNoInteractions(publisher);
        assertThat(registry.activeTimerCount()).isZero();
    }

    @Test
    void gracefulShutdownWaitsForAnInFlightPublicationToFinish() throws Exception {
        registry = registry(10);
        EscalationStepSchedule overdue = step(UUID.randomUUID(), 0, START.minusSeconds(1));
        when(scheduling.isStepStillPendingForPublication(overdue)).thenReturn(true);
        CountDownLatch publicationEntered = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        CountDownLatch shutdownComplete = new CountDownLatch(1);
        doAnswer(invocation -> {
            publicationEntered.countDown();
            assertThat(releasePublication.await(3, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(publisher).publishEscalationStepReady(any(EscalationStepReadyMessage.class));

        assertThat(registry.tryScheduleInMemoryTimer(overdue)).isTrue();
        takeCall().runnable().run();
        assertThat(publicationEntered.await(3, TimeUnit.SECONDS)).isTrue();

        ExecutorService stopper = Executors.newSingleThreadExecutor();
        try {
            stopper.execute(() -> {
                registry.stop();
                shutdownComplete.countDown();
            });
            assertThat(shutdownComplete.await(100, TimeUnit.MILLISECONDS)).isFalse();
            releasePublication.countDown();
            assertThat(shutdownComplete.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            releasePublication.countDown();
            stopper.shutdownNow();
        }

        verify(scheduling).markPublished(overdue);
        assertThat(registry.activeTimerCount()).isZero();
    }

    private StepTimerRegistry registry(int maxTimers) {
        return new StepTimerRegistry(
                taskScheduler, clock, scheduling, publisher, events,
                Duration.ofSeconds(5), maxTimers, 1, 1);
    }

    private CountDownLatch listenForSlotFreed() {
        return listenForSlotFreed(1);
    }

    private CountDownLatch listenForSlotFreed(int count) {
        CountDownLatch latch = new CountDownLatch(count);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof TimerCapacityAvailable) {
                latch.countDown();
            }
            return null;
        }).when(events).publishEvent(any(Object.class));
        return latch;
    }

    private ScheduledCall takeCall() throws InterruptedException {
        ScheduledCall call = calls.poll(3, TimeUnit.SECONDS);
        assertThat(call).as("scheduled timer callback").isNotNull();
        return call;
    }

    private static EscalationStepSchedule step(UUID id, int attempt, Instant dueAt) {
        return new EscalationStepSchedule(id, attempt, dueAt);
    }

    private record ScheduledCall(Runnable runnable, Instant dueAt, ScheduledFuture<?> future) {
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
