package com.alertops.flow_execution_engine.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.alertops.flow_execution_engine.messaging.EscalationStartFailureNotificationRequested;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStartFailureNotification;
import com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.EscalationStartFailureNotificationRepository;
import com.alertops.messaging.Notification;
import com.alertops.team.repository.FailureNotificationRecipientProjection;
import com.alertops.team.repository.TeamMemberRepository;

@Service
public class EscalationStartFailureNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(EscalationStartFailureNotificationService.class);
    private static final String DEFAULT_REASON = "SCHEDULED_START_FAILED";
    private static final int MAX_ERROR_LENGTH = 500;
    private static final List<EscalationStartFailureNotificationStatus> RECOVERABLE_STATUSES = List.of(
            EscalationStartFailureNotificationStatus.PENDING,
            EscalationStartFailureNotificationStatus.SENDING);

    private final EscalationStartFailureNotificationRepository notificationRepository;
    private final EscalationRepository escalationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final Notification notification;
    private final Clock clock;
    private final TaskScheduler taskScheduler;
    private final Duration retryDelay;
    private final Duration deliveryLease;
    private ScheduledFuture<?> recoveryTimer;
    private volatile boolean shuttingDown;

    // Validates dependencies and retry timing used by this service.
    public EscalationStartFailureNotificationService(
            EscalationStartFailureNotificationRepository notificationRepository,
            EscalationRepository escalationRepository,
            TeamMemberRepository teamMemberRepository,
            Notification notification,
            Clock clock,
            TaskScheduler taskScheduler,
            @Value("${alertops.notifications.failure-retry-delay:5m}") Duration retryDelay,
            @Value("${alertops.notifications.failure-delivery-lease:1m}") Duration deliveryLease) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.teamMemberRepository = Objects.requireNonNull(teamMemberRepository, "teamMemberRepository");
        this.notification = Objects.requireNonNull(notification, "notification");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Failure-notification retry delay must be positive");
        }
        if (deliveryLease == null || deliveryLease.isZero() || deliveryLease.isNegative()) {
            throw new IllegalArgumentException("Failure-notification delivery lease must be positive");
        }
        this.retryDelay = retryDelay;
        this.deliveryLease = deliveryLease;
    }

    // Creates one durable pending notification for each responsible recipient.
    @Transactional
    public void createPendingNotifications(Escalation escalation, String failureReason) {
        if (escalation == null || escalation.getId() == null || escalation.getTeamId() == null) {
            throw new IllegalArgumentException("A failed escalation with an ID and team is required");
        }

        Instant now = clock.instant();
        String reason = normalizeReason(failureReason);
        // Store a recipient snapshot so delivery can still happen after a restart or membership change.
        Map<String, Recipient> recipients = resolveRecipients(escalation);
        if (recipients.isEmpty()) {
            logger.warn("Scheduled escalation {} reached START_FAILED without a notification recipient",
                    escalation.getId());
        }
        for (Recipient recipient : recipients.values()) {
            if (notificationRepository.existsByEscalationIdAndRecipientEmailIgnoreCase(
                    escalation.getId(), recipient.email())) {
                continue;
            }

            EscalationStartFailureNotification pending = new EscalationStartFailureNotification();
            pending.setEscalationId(escalation.getId());
            pending.setRecipientUserId(recipient.userId());
            pending.setRecipientEmail(recipient.email());
            pending.setReason(reason);
            pending.setStatus(EscalationStartFailureNotificationStatus.PENDING);
            pending.setAttemptCount(0);
            pending.setNextAttemptAt(now);
            pending.setCreatedAt(now);
            pending.setUpdatedAt(now);
            notificationRepository.save(pending);
        }
    }

    // Recovers pending notifications when this application instance becomes ready.
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingNotifications() {
        Instant now = clock.instant();
        try {
            // A crash may leave a row in SENDING. No old process can finish it after restart,
            // so release it immediately instead of waiting for the old lease to expire.
            notificationRepository.requeueInFlightForRecovery(
                    now, "Recovered after application restart before delivery completed");
        } catch (RuntimeException e) {
            logger.warn("Could not release in-flight scheduled-start failure notifications", e);
        }
        deliverDueNotifications();
    }

    // Loads due rows and attempts delivery for each one.
    private void deliverDueNotifications() {
        Instant now = clock.instant();
        List<UUID> recoverableIds;
        try {
            recoverableIds = notificationRepository.findRecoverableIds(RECOVERABLE_STATUSES, now);
        } catch (RuntimeException e) {
            logger.warn("Could not load pending scheduled-start failure notifications; recovery will retry", e);
            scheduleRecoveryAfterFailure();
            return;
        }
        if (recoverableIds == null || recoverableIds.isEmpty()) {
            scheduleRecoveryIfNeeded();
            return;
        }
        // Claiming is conditional in the repository, so duplicate callbacks or application
        // instances cannot both deliver the same row at the same time.
        for (UUID notificationId : recoverableIds) {
            attemptDelivery(notificationId);
        }
        scheduleRecoveryIfNeeded();
    }

    // Starts delivery after the START_FAILED transaction commits successfully.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFailureRecorded(EscalationStartFailureNotificationRequested event) {
        if (event == null || event.escalationId() == null) {
            return;
        }
        deliverPendingForEscalation(event.escalationId());
    }

    // Attempts newly-created rows immediately after START_FAILED is committed.
    public void deliverPendingForEscalation(UUID escalationId) {
        if (escalationId == null) {
            return;
        }
        Instant now = clock.instant();
        List<UUID> recoverableIds;
        try {
            recoverableIds = notificationRepository.findRecoverableIdsForEscalation(
                    escalationId, RECOVERABLE_STATUSES, now);
        } catch (RuntimeException e) {
            logger.warn("Could not load pending failure notifications for escalation {}", escalationId, e);
            scheduleRecoveryAfterFailure();
            return;
        }
        if (recoverableIds == null || recoverableIds.isEmpty()) {
            return;
        }
        for (UUID notificationId : recoverableIds) {
            attemptDelivery(notificationId);
        }
        scheduleRecoveryIfNeeded();
    }

    // Claims one row, sends its email, and records SENT or a retryable failure.
    private void attemptDelivery(UUID notificationId) {
        if (notificationId == null) {
            return;
        }
        Instant now = clock.instant();
        int claimed;
        try {
            // The lease makes a claimed row recoverable if this process crashes before markSent.
            claimed = notificationRepository.claimForDelivery(
                    notificationId, now, now.plus(deliveryLease));
        } catch (RuntimeException e) {
            logger.warn("Could not claim scheduled-start failure notification {}", notificationId, e);
            scheduleRecoveryAfterFailure();
            return;
        }
        if (claimed != 1) {
            return;
        }

        Optional<EscalationStartFailureNotification> pending;
        try {
            pending = notificationRepository.findById(notificationId);
        } catch (RuntimeException e) {
            logger.warn("Could not load scheduled-start failure notification {} after claiming it",
                    notificationId, e);
            scheduleRecoveryAfterFailure();
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        EscalationStartFailureNotification obligation = pending.get();
        Optional<Escalation> escalation;
        try {
            escalation = escalationRepository.findById(obligation.getEscalationId());
        } catch (RuntimeException e) {
            logger.warn("Could not load escalation {} for scheduled-start failure notification {}",
                    obligation.getEscalationId(), notificationId, e);
            markPendingAndSchedule(
                    notificationId, "The failed escalation could not be reloaded");
            return;
        }
        // Reload the escalation instead of trusting an in-memory object from the failure callback.
        if (escalation.isEmpty()) {
            markPendingAndSchedule(
                    notificationId, "The failed escalation could not be reloaded");
            return;
        }

        boolean delivered = notification.sendStartFailureEmail(
                escalation.get().getId(),
                escalation.get().getName(),
                obligation.getRecipientEmail(),
                obligation.getReason());

        if (!delivered) {
            markPendingAndSchedule(
                    notificationId, "The email provider did not accept the notification");
            return;
        }

        markDelivered(notificationId);
    }

    // Changes a successfully delivered notification from SENDING to SENT.
    private void markDelivered(UUID notificationId) {
        try {
            // SENT is the durable acknowledgement that prevents normal recovery from resending.
            notificationRepository.markSent(notificationId, clock.instant());
        } catch (RuntimeException e) {
            logger.warn("Could not record delivery for scheduled-start failure notification {}", notificationId, e);
            scheduleRecoveryAfterFailure();
        }
    }

    // Returns a failed delivery to PENDING and schedules a future recovery attempt.
    private void markPendingAndSchedule(UUID notificationId, String error) {
        try {
            notificationRepository.markPending(notificationId, clock.instant().plus(retryDelay), error);
        } catch (RuntimeException e) {
            logger.warn("Could not release scheduled-start failure notification {} for retry", notificationId, e);
        }
        scheduleRecoveryAfterFailure();
    }

    // Schedules recovery after a temporary delivery or database failure.
    private void scheduleRecoveryAfterFailure() {
        scheduleRecoveryAt(clock.instant().plus(retryDelay));
    }

    // Schedules the next database-backed notification retry when one exists.
    private synchronized void scheduleRecoveryIfNeeded() {
        if (shuttingDown || recoveryTimer != null && !recoveryTimer.isDone()) {
            return;
        }
        Instant now = clock.instant();
        Instant nextAttemptAt;
        try {
            // The database tells us when the next pending/leased row is eligible; memory only
            // holds the callback that wakes this service at that time.
            nextAttemptAt = notificationRepository.findNextAttemptAt(RECOVERABLE_STATUSES);
        } catch (RuntimeException e) {
            logger.warn("Could not determine the next scheduled-start failure notification retry", e);
            scheduleRecoveryAt(now.plus(retryDelay));
            return;
        }
        if (nextAttemptAt == null) {
            return;
        }
        scheduleRecoveryAt(nextAttemptAt.isAfter(now) ? nextAttemptAt : now.plus(retryDelay));
    }

    // Keeps only one in-memory wake-up timer for notification recovery.
    private synchronized void scheduleRecoveryAt(Instant requestedAt) {
        if (shuttingDown || recoveryTimer != null && !recoveryTimer.isDone()) {
            return;
        }
        Instant now = clock.instant();
        Instant retryAt = requestedAt == null || !requestedAt.isAfter(now)
                ? now.plus(retryDelay)
                : requestedAt;
        try {
            recoveryTimer = taskScheduler.schedule(this::runRecovery, retryAt);
        } catch (RuntimeException e) {
            logger.warn("Could not schedule scheduled-start failure notification recovery", e);
        }
    }

    // Clears the wake-up handle and loads due rows again.
    private void runRecovery() {
        synchronized (this) {
            recoveryTimer = null;
        }
        if (!shuttingDown) {
            deliverDueNotifications();
        }
    }

    // Cancels the recovery wake-up during application shutdown.
    @PreDestroy
    void stop() {
        shuttingDown = true;
        synchronized (this) {
            if (recoveryTimer != null) {
                recoveryTimer.cancel(false);
                recoveryTimer = null;
            }
        }
    }

    // Resolves the scheduler plus current team owners and administrators.
    private Map<String, Recipient> resolveRecipients(Escalation escalation) {
        Map<String, Recipient> recipients = new LinkedHashMap<>();
        // Include the scheduler even if they are no longer an owner/admin today.
        addRecipient(recipients, escalation.getScheduledByUserId(), escalation.getScheduledByUserEmail());

        List<FailureNotificationRecipientProjection> teamRecipients =
                teamMemberRepository.findFailureNotificationRecipients(escalation.getTeamId());
        if (teamRecipients == null) {
            return recipients;
        }
        for (FailureNotificationRecipientProjection recipient : teamRecipients) {
            if (recipient == null) {
                continue;
            }
            addRecipient(recipients, recipient.getUserId(), recipient.getEmail());
        }
        return recipients;
    }

    // Adds a nonblank recipient once using case-insensitive email matching.
    private void addRecipient(Map<String, Recipient> recipients, UUID userId, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim();
        // Email matching is case-insensitive for deduplication, while the original trimmed
        // address is retained for sending and for the audit trail.
        recipients.putIfAbsent(normalizedEmail.toLowerCase(Locale.ROOT), new Recipient(userId, normalizedEmail));
    }

    // Supplies a safe default and length limit for the persisted failure reason.
    private String normalizeReason(String failureReason) {
        String reason = failureReason == null || failureReason.isBlank()
                ? DEFAULT_REASON
                : failureReason.trim();
        return reason.length() <= MAX_ERROR_LENGTH
                ? reason
                : reason.substring(0, MAX_ERROR_LENGTH);
    }

    private record Recipient(UUID userId, String email) {
    }
}
