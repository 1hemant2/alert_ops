package com.alertops.flow_execution_engine.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.messaging.EscalationStartFailureNotificationRequested;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

/** Persists the retry policy for scheduled escalation starts. */
@Service
public class EscalationStartRetryService {
    private final EscalationRepository escalationRepository;
    private final AuditService auditService;
    private final EscalationStartFailureNotificationService failureNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxRetries;

    public EscalationStartRetryService(
            EscalationRepository escalationRepository,
            @Value("${alertops.scheduler.start-max-retries:3}") int maxRetries,
            AuditService auditService,
            EscalationStartFailureNotificationService failureNotificationService,
            ApplicationEventPublisher eventPublisher) {
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.failureNotificationService = Objects.requireNonNull(
                failureNotificationService, "failureNotificationService");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Scheduled-start max retries cannot be negative");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Records one failed start and returns the next retry time when another retry is allowed.
     * The row lock keeps the retry count correct when more than one callback sees the same
     * scheduled escalation.
     */
    @Transactional
    public Optional<Instant> recordFailureAndPlanRetry(
            UUID escalationId, Instant retryAt, String failureReason) {
        if (escalationId == null || retryAt == null) {
            return Optional.empty();
        }

        Escalation escalation = escalationRepository.findByIdForUpdate(escalationId).orElse(null);
        if (escalation == null || escalation.getStatus() != EscalationStatus.SCHEDULED) {
            return Optional.empty();
        }

        int retriesUsed = escalation.getScheduledStartRetryCount();
        if (retriesUsed >= maxRetries) {
            escalation.setStatus(EscalationStatus.START_FAILED);
            escalation.setScheduledStartNextRetryAt(null);
            escalationRepository.save(escalation);
            failureNotificationService.createPendingNotifications(escalation, failureReason);
            auditService.record(new AuditEvent(
                    AuditEntityType.ESCALATION, escalationId, AuditAction.START_FAILED,
                    EscalationStatus.SCHEDULED.name(), EscalationStatus.START_FAILED.name(),
                    null, null, Instant.now(), failureReason, null));
            eventPublisher.publishEvent(new EscalationStartFailureNotificationRequested(escalationId));
            return Optional.empty();
        }

        escalation.setScheduledStartRetryCount(retriesUsed + 1);
        escalation.setScheduledStartNextRetryAt(retryAt);
        escalationRepository.save(escalation);
        return Optional.of(retryAt);
    }
}
