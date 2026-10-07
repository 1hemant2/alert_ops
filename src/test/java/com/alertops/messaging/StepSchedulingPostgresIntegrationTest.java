package com.alertops.messaging;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.alertops.flow.model.Node;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.audit.repository.AuditEventRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.flow_execution_engine.service.FlowExecutionStartMode;
import com.alertops.flow_execution_engine.service.EscalationAcknowledgementService;
import com.alertops.audit.service.AuditService;
import com.alertops.task.model.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** PostgreSQL persistence checks; RabbitMQ is represented by a confirmable mock. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TEST", matches = "true")
@SpringBootTest(classes = StepSchedulingPostgresIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StepSchedulingPostgresIntegrationTest {
    private static final String SCHEMA = "alertops_schedule_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final BlockingQueue<TimerCall> TIMER_CALLS = new LinkedBlockingQueue<>();
    private static final AdjustableClock CLOCK = new AdjustableClock(Instant.now());

    @Autowired private StepSchedulingService scheduling;
    @Autowired private StepTimerRegistry timers;
    @Autowired private ReconcilerService reconciler;
    @Autowired private MessagePublisher publisher;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TaskScheduler timerScheduler;
    @Autowired private MessageConsumer consumer;
    @Autowired private FlowExecutionStateRepository states;
    @Autowired private EscalationRepository escalations;
    @Autowired private AuditEventRepository auditEvents;
    @Autowired private EscalationAcknowledgementTokenRepository acknowledgementTokens;
    @Autowired private FlowExecutionStateService flowExecutionStateService;
    @Autowired private EscalationAcknowledgementService acknowledgementService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private Notification notification;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) throws SQLException {
        try (Connection connection = databaseConnection()) {
            connection.createStatement().execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> requiredEnvironment("POSTGRES_INTEGRATION_URL"));
        properties.add("spring.datasource.username", () -> requiredEnvironment("POSTGRES_INTEGRATION_USERNAME"));
        properties.add("spring.datasource.password", () -> requiredEnvironment("POSTGRES_INTEGRATION_PASSWORD"));
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @AfterAll
    static void removeSchema() throws SQLException {
        try (Connection connection = databaseConnection()) {
            connection.createStatement().execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach
    // Resets durable rows, broker mocks, and captured timer callbacks.
    void resetDataAndBroker() {
        TIMER_CALLS.clear();
        CLOCK.set(Instant.now());
        reset(rabbit, notification);
        states.deleteAll();
        acknowledgementTokens.deleteAll();
        auditEvents.deleteAll();
        escalations.deleteAll();
        confirmPublishes();
    }

    @AfterEach
    // Clears durable rows before the next Spring context starts.
    void clearRowsBeforeTheNextSpringContextStarts() {
        states.deleteAll();
        acknowledgementTokens.deleteAll();
        auditEvents.deleteAll();
        escalations.deleteAll();
    }

    @Test
    // Verifies that zero-delay delivery begins only after its save commits.
    void zeroDelayStepPublishesOnlyAfterItsDatabaseCommit() throws Exception {
        when(notification.sendEmail(any(), anyString(), any())).thenReturn(true);
        CountDownLatch delivered = new CountDownLatch(1);
        doAnswer(invocation -> {
            EscalationStepReadyMessage payload = invocation.getArgument(2);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement query = connection.prepareStatement(
                         "SELECT status FROM flow_execution_state WHERE id = ?")) {
                query.setObject(1, payload.stepId());
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo(FlowExecutionStepStatus.SCHEDULED.name());
                }
            }
            consumer.deliverReadyStep(payload);
            acknowledge(invocation.getArgument(4));
            delivered.countDown();
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));

        UUID stepId = transaction().execute(status -> {
            FlowExecutionState step = createStep(FlowExecutionStepStatus.PENDING, Duration.ZERO, false, null);
            scheduling.scheduleStep(step);
            verifyNoInteractions(rabbit);
            return step.getId();
        });

        TimerCall due = takeTimer();
        CLOCK.set(Instant.now().plusSeconds(1));
        due.runnable().run();
        assertThat(delivered.await(3, TimeUnit.SECONDS)).isTrue();

        FlowExecutionState saved = states.findById(stepId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FlowExecutionStepStatus.SENT);
        assertThat(saved.isPublicationPending()).isFalse();
        verify(notification, times(1)).sendEmail(any(), anyString(), any());
    }

    @Test
    // Verifies that rolled-back scheduling creates no timer or publication.
    void rolledBackSchedulingDoesNotRegisterATimerOrPublish() {
        UUID stepId = transaction().execute(status -> {
            FlowExecutionState step = createStep(FlowExecutionStepStatus.PENDING, Duration.ofMinutes(5), false, null);
            scheduling.scheduleStep(step);
            status.setRollbackOnly();
            return step.getId();
        });

        assertThat(states.findById(stepId)).isEmpty();
        assertThat(TIMER_CALLS).isEmpty();
        assertThat(timers.activeTimerCount()).isZero();
        verifyNoInteractions(rabbit);
    }

    @Test
    // Verifies that a committed schedule stays pending until confirmation.
    void committedScheduleStaysPendingUntilConfirmation() throws Exception {
        UUID stepId = transaction().execute(status -> {
            FlowExecutionState step = createStep(FlowExecutionStepStatus.PENDING, Duration.ofMinutes(5), false, null);
            scheduling.scheduleStep(step);
            assertThat(step.isPublicationPending()).isTrue();
            verifyNoInteractions(rabbit);
            return step.getId();
        });

        TimerCall timer = takeTimer();
        FlowExecutionState saved = states.findById(stepId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FlowExecutionStepStatus.SCHEDULED);
        assertThat(saved.getDueAt()).isEqualTo(timer.dueAt());
        assertThat(saved.isPublicationPending()).isTrue();
        verifyNoInteractions(rabbit);
    }

    @Test
    // Verifies that broker failure retries without changing the business due time.
    void brokerFailureRetriesWithoutChangingTheBusinessDueTime() throws Exception {
        doThrow(new IllegalStateException("Broker unavailable"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class),
                        any(CorrelationData.class));

        UUID stepId = transaction().execute(status -> {
            FlowExecutionState step = createStep(FlowExecutionStepStatus.PENDING, Duration.ZERO, false, null);
            scheduling.scheduleStep(step);
            return step.getId();
        });
        Instant originalDueAt = states.findById(stepId).orElseThrow().getDueAt();
        TimerCall firstAttempt = takeTimer();
        CLOCK.set(Instant.now().plusSeconds(1));
        firstAttempt.runnable().run();

        TimerCall retry = takeTimer();
        assertThat(retry.dueAt()).isAfter(CLOCK.instant());
        FlowExecutionState waiting = states.findById(stepId).orElseThrow();
        assertThat(waiting.isPublicationPending()).isTrue();
        assertThat(waiting.getDueAt()).isEqualTo(originalDueAt);

        confirmPublishes();
        CLOCK.set(retry.dueAt());
        retry.runnable().run();
        awaitPendingFlag(stepId, false);
        assertThat(states.findById(stepId).orElseThrow().getDueAt()).isEqualTo(originalDueAt);
    }

    @Test
    // Verifies that startup recovery loads more than one recovery page.
    void startupRecoveryPagesPastOneHundredSavedSchedules() throws Exception {
        transaction().executeWithoutResult(status -> {
            for (int index = 0; index < 205; index++) {
                createStep(FlowExecutionStepStatus.SCHEDULED, Duration.ZERO, true, Instant.now().plusSeconds(3600L + index));
            }
        });

        reconciler.onStartup();

        assertThat(timers.activeTimerCount()).isEqualTo(205);
        assertThat(TIMER_CALLS).hasSize(205);
        assertThat(scheduling.countPendingSchedules()).isEqualTo(205);
    }

    @Test
    // Verifies that startup recovery restores an unregistered durable schedule.
    void startupRecoveryRestoresUnregisteredSchedule() throws Exception {
        Instant dueAt = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        FlowExecutionState saved = transaction().execute(
                status -> createStep(FlowExecutionStepStatus.SCHEDULED, Duration.ZERO, true, dueAt));
        Instant persistedDueAt = states.findById(saved.getId()).orElseThrow().getDueAt();
        assertThat(timers.activeTimerCount()).isZero();

        reconciler.onStartup();

        TimerCall restored = takeTimer();
        assertThat(restored.dueAt()).isEqualTo(persistedDueAt);
        assertThat(timers.activeTimerCount()).isEqualTo(1);
        assertThat(states.findById(saved.getId()).orElseThrow().isPublicationPending()).isTrue();
    }

    @Test
    // Verifies that a new timer registry preserves the original due time.
    void timerRegistryRestoresOriginalDueTime() throws Exception {
        UUID stepId = transaction().execute(status -> {
            FlowExecutionState step = createStep(FlowExecutionStepStatus.PENDING, Duration.ofMinutes(5), false, null);
            scheduling.scheduleStep(step);
            return step.getId();
        });
        takeTimer();
        Instant persistedDueAt = states.findById(stepId).orElseThrow().getDueAt();
        assertThat(timers.activeTimerCount()).isEqualTo(1);

        // Simulate the old process stopping: its in-memory timer disappears while the DB row remains.
        timers.stop();
        TIMER_CALLS.clear();
        StepTimerRegistry restartedTimers = new StepTimerRegistry(
                timerScheduler, CLOCK, scheduling, publisher, events,
                Duration.ofSeconds(5), 10000, 1, 10);
        try {
            ReconcilerService restartedReconciler = new ReconcilerService(
                    scheduling, restartedTimers, timerScheduler, Duration.ofSeconds(5));
            restartedReconciler.onStartup();

            TimerCall restored = takeTimer();
            assertThat(restored.dueAt()).isEqualTo(persistedDueAt);
            assertThat(restartedTimers.activeTimerCount()).isEqualTo(1);
            assertThat(states.findById(stepId).orElseThrow().isPublicationPending()).isTrue();
            verifyNoInteractions(rabbit);
        } finally {
            restartedTimers.stop();
        }
    }

    @Test
    // Verifies that an early ready message is rescheduled without claiming.
    void earlyReadyMessageReschedulesWithoutClaiming() {
        Instant dueAt = Instant.now().plus(Duration.ofMinutes(5));
        FlowExecutionState step = transaction().execute(status -> createStep(FlowExecutionStepStatus.SCHEDULED, Duration.ZERO, false, dueAt));
        Instant persistedDueAt = states.findById(step.getId()).orElseThrow().getDueAt();

        consumer.deliverReadyStep(new EscalationStepReadyMessage(step.getId(), 0, persistedDueAt));

        FlowExecutionState waiting = states.findById(step.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(FlowExecutionStepStatus.SCHEDULED);
        assertThat(waiting.isPublicationPending()).isTrue();
        assertThat(timers.activeTimerCount()).isEqualTo(1);
        verifyNoInteractions(notification, rabbit);
    }

    @Test
    // Verifies that stale publication confirmation cannot clear newer work.
    void staleConfirmationCannotClearNewerWork() {
        Instant dueAt = Instant.now().plus(Duration.ofMinutes(5));
        UUID stepId = transaction().execute(status -> {
            FlowExecutionState step = createStep(FlowExecutionStepStatus.SCHEDULED, Duration.ofMinutes(5), true, dueAt);
            step.setSendAttemptCount(1);
            return states.save(step).getId();
        });

        scheduling.markPublished(new EscalationStepSchedule(stepId, 0, dueAt));
        scheduling.markPublished(new EscalationStepSchedule(stepId, 1, dueAt.plusSeconds(1)));
        assertThat(states.findById(stepId).orElseThrow().isPublicationPending()).isTrue();

        scheduling.markPublished(new EscalationStepSchedule(stepId, 1, dueAt));
        assertThat(states.findById(stepId).orElseThrow().isPublicationPending()).isFalse();
    }

    @Test
    // Verifies that concurrent consumers claim one delivery attempt only once.
    void concurrentReadyMessagesClaimOnce() throws Exception {
        FlowExecutionState step = transaction().execute(status -> createStep(
                FlowExecutionStepStatus.SCHEDULED, Duration.ZERO, true, Instant.now().minusSeconds(1)));
        ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> transaction().execute(status -> states.claimForDelivery(
                            step.getId(), 0, step.getDueAt())), workers);
            var second = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> transaction().execute(status -> states.claimForDelivery(
                            step.getId(), 0, step.getDueAt())), workers);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        } finally {
            workers.shutdownNow();
        }
        FlowExecutionState claimed = states.findById(step.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(FlowExecutionStepStatus.SENDING);
        assertThat(claimed.isPublicationPending()).isFalse();
    }

    @Test
    // Verifies that acknowledgement waits for delivery and pauses the next step.
    void acknowledgementWaitsForDeliveryAndPausesNextStep() throws Exception {
        CountDownLatch emailStarted = new CountDownLatch(1);
        CountDownLatch finishEmail = new CountDownLatch(1);
        when(notification.sendEmail(any(), anyString(), any())).thenAnswer(invocation -> {
            emailStarted.countDown();
            assertThat(finishEmail.await(10, TimeUnit.SECONDS)).isTrue();
            return true;
        });

        Instant dueAt = Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        FlowExecutionState activeStep = transaction().execute(
                status -> createStep(FlowExecutionStepStatus.SCHEDULED, Duration.ZERO, false, dueAt));
        FlowExecutionState nextStep = transaction().execute(status -> {
            FlowExecutionState step = new FlowExecutionState();
            step.setProcessId(activeStep.getProcessId());
            step.setStatus(FlowExecutionStepStatus.PENDING);
            step.setDuration(Duration.ZERO);
            step.setUserEmail("recipient@example.test");
            return states.save(step);
        });
        String link = transaction().execute(status -> acknowledgementService.createAcknowledgementUrl(
                escalations.findById(activeStep.getProcessId()).orElseThrow(), activeStep));
        String rawToken = link.substring(link.indexOf("token=") + "token=".length());

        CountDownLatch acknowledgementStarted = new CountDownLatch(1);
        ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            Future<?> delivery = workers.submit(() -> consumer.deliverReadyStep(
                    new EscalationStepReadyMessage(activeStep.getId(), 0, dueAt)));
            assertThat(emailStarted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> acknowledgement = workers.submit(() -> {
                acknowledgementStarted.countDown();
                return acknowledgementService.acknowledge(rawToken);
            });
            assertThat(acknowledgementStarted.await(10, TimeUnit.SECONDS)).isTrue();
            boolean waitedForEmail = false;
            try {
                acknowledgement.get(200, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException expected) {
                waitedForEmail = true;
            }
            assertThat(waitedForEmail).isTrue();

            finishEmail.countDown();
            delivery.get(10, TimeUnit.SECONDS);
            acknowledgement.get(10, TimeUnit.SECONDS);
        } finally {
            finishEmail.countDown();
            workers.shutdownNow();
        }

        Escalation acknowledged = escalations.findById(activeStep.getProcessId()).orElseThrow();
        assertThat(acknowledged.getStatus()).isEqualTo(EscalationStatus.COMPLETED);
        assertThat(acknowledged.getResolutionType()).isEqualTo(EscalationResolutionType.ACKNOWLEDGED);
        assertThat(acknowledged.getIssueSolvedBy()).isEqualTo("recipient@example.test");
        assertThat(states.findById(nextStep.getId()).orElseThrow().getStatus())
                .isEqualTo(FlowExecutionStepStatus.SKIPPED);

        Instant lateDueAt = Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        consumer.deliverReadyStep(new EscalationStepReadyMessage(nextStep.getId(), 0, lateDueAt));

        verify(notification, times(1)).sendEmail(any(), anyString(), any());
    }

    @Test
    // Verifies that concurrent starts create one execution-state set.
    void concurrentStartsCreateOnlyOneSetOfExecutionStates() throws Exception {
        UUID teamId = UUID.randomUUID();
        Escalation escalation = new Escalation();
        escalation.setStatus(EscalationStatus.IDLE);
        escalation.setTeamId(teamId);
        UUID escalationId = escalations.saveAndFlush(escalation).getId();

        Task task = new Task();
        task.setDescription("Duplicate start test");
        List<Node> nodes = List.of(node(0, "first@example.test"), node(1, "second@example.test"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = submitStartAttempt(
                    workers, ready, start, task, nodes, escalationId, teamId);
            Future<Throwable> second = submitStartAttempt(
                    workers, ready, start, task, nodes, escalationId, teamId);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Throwable firstFailure = first.get(10, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(10, TimeUnit.SECONDS);
            int successfulStarts = (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
            assertThat(successfulStarts).isEqualTo(1);

            Throwable conflict = firstFailure == null ? secondFailure : firstFailure;
            assertThat(conflict).isInstanceOf(EscalationException.class);
            assertThat(((EscalationException) conflict).getCode())
                    .isEqualTo("ESCALATION_START_CONFLICT");
        } finally {
            workers.shutdownNow();
        }

        Escalation started = escalations.findById(escalationId).orElseThrow();
        assertThat(started.getStatus()).isEqualTo(EscalationStatus.OPEN);
        assertThat(auditEvents.findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                AuditEntityType.ESCALATION.name(), escalationId))
                .extracting(AuditEventEntity::getAction)
                .contains("STARTED");
        assertThat(states.findAllByProcessIdOrderByPositionAsc(escalationId)).hasSize(2);
        assertThat(timers.activeTimerCount()).isEqualTo(1);
    }

    @Test
    // Verifies that the database rejects unsupported lifecycle values.
    void databaseRejectsUnknownEscalationLifecycleValues() {
        Escalation escalation = new Escalation();
        escalation.setStatus(EscalationStatus.IDLE);
        UUID escalationId = escalations.saveAndFlush(escalation).getId();

        assertThatThrownBy(() -> updateEscalationColumn(escalationId, "status", "NOT_A_STATUS"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> updateEscalationColumn(escalationId, "resolution_type", "NOT_A_RESOLUTION"))
                .isInstanceOf(SQLException.class);

        FlowExecutionState step = transaction().execute(
                status -> createStep(FlowExecutionStepStatus.PENDING, Duration.ZERO, false, null));
        assertThatThrownBy(() -> updateExecutionStateColumn(step.getId(), "status", "NOT_A_STEP_STATUS"))
                .isInstanceOf(SQLException.class);
    }

    private void updateEscalationColumn(UUID escalationId, String column, String value) throws SQLException {
        String sql = "UPDATE " + SCHEMA + ".escalation SET " + column + " = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setObject(2, escalationId);
            statement.executeUpdate();
        }
    }

    private void updateExecutionStateColumn(UUID stepId, String column, String value) throws SQLException {
        String sql = "UPDATE " + SCHEMA + ".flow_execution_state SET " + column + " = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setObject(2, stepId);
            statement.executeUpdate();
        }
    }

    private Future<Throwable> submitStartAttempt(
            ExecutorService workers,
            CountDownLatch ready,
            CountDownLatch start,
            Task task,
            List<Node> nodes,
            UUID escalationId,
            UUID teamId
    ) {
        return workers.submit(() -> {
            ready.countDown();
            start.await();
            try {
                flowExecutionStateService.startFlowExecution(
                        task, new com.alertops.flow.model.Flow(), nodes, escalationId, teamId, FlowExecutionStartMode.IDLE);
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        });
    }

    private static Node node(int position, String email) {
        Node node = new Node();
        node.setPosition(BigInteger.valueOf(position));
        node.setDuration(Duration.ofMinutes(5));
        node.setEmail(email);
        return node;
    }

    private FlowExecutionState createStep(FlowExecutionStepStatus status, Duration duration, boolean pending, Instant dueAt) {
        Escalation escalation = new Escalation();
        escalation.setStatus(EscalationStatus.OPEN);
        escalations.save(escalation);
        FlowExecutionState step = new FlowExecutionState();
        step.setProcessId(escalation.getId());
        step.setStatus(status);
        step.setDuration(duration);
        step.setDueAt(dueAt);
        step.setPublicationPending(pending);
        step.setUserEmail("recipient@example.test");
        return states.save(step);
    }

    private TimerCall takeTimer() throws InterruptedException {
        TimerCall timer = TIMER_CALLS.poll(3, TimeUnit.SECONDS);
        assertThat(timer).as("registered in-memory timer").isNotNull();
        return timer;
    }

    private void awaitPendingFlag(UUID stepId, boolean expected) throws InterruptedException {
        long timeoutAtNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < timeoutAtNanos) {
            if (states.findById(stepId).orElseThrow().isPublicationPending() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(states.findById(stepId).orElseThrow().isPublicationPending()).isEqualTo(expected);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void confirmPublishes() {
        doAnswer(invocation -> {
            acknowledge(invocation.getArgument(4));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    private static void acknowledge(CorrelationData correlation) {
        correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
    }

    private static Connection databaseConnection() throws SQLException {
        return DriverManager.getConnection(requiredEnvironment("POSTGRES_INTEGRATION_URL"),
                requiredEnvironment("POSTGRES_INTEGRATION_USERNAME"), requiredEnvironment("POSTGRES_INTEGRATION_PASSWORD"));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private record TimerCall(Runnable runnable, Instant dueAt) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {RabbitAutoConfiguration.class, DataRedisAutoConfiguration.class,
            DataRedisRepositoriesAutoConfiguration.class})
    @EntityScan(basePackageClasses = {FlowExecutionState.class, AuditEventEntity.class})
    @EnableJpaRepositories(basePackageClasses = {FlowExecutionStateRepository.class, AuditEventRepository.class})
    @Import({StepSchedulingService.class, StepTimerRegistry.class, ReconcilerService.class,
            MessagePublisher.class, MessageConsumer.class, FlowExecutionStateService.class,
            EscalationAcknowledgementService.class, EscalationTimeoutService.class, AuditService.class})
    static class Config {
        @Bean RabbitTemplate rabbitTemplate() { return mock(RabbitTemplate.class); }

        @Bean TaskScheduler taskScheduler() {
            TaskScheduler scheduler = mock(TaskScheduler.class);
            doAnswer(invocation -> {
                ScheduledFuture<?> future = mock(ScheduledFuture.class);
                TIMER_CALLS.add(new TimerCall(invocation.getArgument(0), invocation.getArgument(1)));
                return future;
            }).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
            return scheduler;
        }

        @Bean Clock clock() { return CLOCK; }
        @Bean Notification notification() { return mock(Notification.class); }
    }

    private static final class AdjustableClock extends Clock {
        private volatile Instant instant;

        private AdjustableClock(Instant instant) { this.instant = instant; }
        private void set(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
