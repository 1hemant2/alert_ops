package com.alertops.flow_execution_engine.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.messaging.EscalationRepeatSchedule;
import com.alertops.flow_execution_engine.messaging.EscalationStartSchedule;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.RepeatType;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

/** Creates one independent escalation run for the next saved repeat. */
@Service
public class EscalationRepeatService {
    private final EscalationRepository escalationRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final EscalationRepeatCalculator repeatCalculator = new EscalationRepeatCalculator();

    // Creates the repeat service with durable escalation and audit dependencies.
    public EscalationRepeatService(
            EscalationRepository escalationRepository,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    // Creates the latest due child and advances the original repeat in one transaction.
    public Optional<Escalation> createLatestDueRun(UUID escalationId, Instant now) {
        if (escalationId == null || now == null) {
            return Optional.empty();
        }

        Escalation original = escalationRepository.findByIdForUpdate(escalationId).orElse(null);
        if (!isRepeating(original) || original.getNextRepeatAt().isAfter(now)) {
            return Optional.empty();
        }

        Instant occurrenceAt = repeatCalculator
                .latestAtOrBefore(
                        original.getScheduledStartAt(),
                        original.getScheduleTimezone(),
                        original.getRepeatType(),
                        now)
                .filter(candidate -> !candidate.isBefore(original.getNextRepeatAt()))
                .orElse(original.getNextRepeatAt());
        Instant nextRepeatAt = repeatCalculator.nextAfter(
                original.getScheduledStartAt(),
                original.getScheduleTimezone(),
                original.getRepeatType(),
                now);

        Escalation child = new Escalation();
        child.setName(original.getName());
        child.setTaskId(original.getTaskId());
        child.setFlowId(original.getFlowId());
        child.setTeamId(original.getTeamId());
        child.setStatus(EscalationStatus.SCHEDULED);
        child.setScheduledStartAt(occurrenceAt);
        child.setScheduleTimezone(original.getScheduleTimezone());
        child.setScheduledByUserId(original.getScheduledByUserId());
        child.setScheduledByUserEmail(original.getScheduledByUserEmail());
        child.setScheduledStartRetryCount(0);
        child.setScheduledStartNextRetryAt(null);
        child.setRepeatType(RepeatType.NONE);
        child.setNextRepeatAt(null);
        child.setRepeatSourceId(original.getId());
        Escalation savedChild = Objects.requireNonNull(
                escalationRepository.save(child), "Saved repeated escalation is required");

        original.setNextRepeatAt(nextRepeatAt);
        escalationRepository.save(original);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                savedChild.getId(),
                AuditAction.REPEAT_CREATED,
                null,
                EscalationStatus.SCHEDULED.name(),
                original.getScheduledByUserId(),
                original.getScheduledByUserEmail(),
                clock.instant(),
                null,
                "repeatSourceId=" + original.getId() + ";scheduledStartAt=" + occurrenceAt));

        eventPublisher.publishEvent(new EscalationStartSchedule(savedChild.getId(), occurrenceAt));
        eventPublisher.publishEvent(new EscalationRepeatSchedule(original.getId(), nextRepeatAt));
        return Optional.of(savedChild);
    }

    // Confirms that the row is an original escalation with a future repeat boundary.
    private boolean isRepeating(Escalation escalation) {
        return escalation != null
                && escalation.getRepeatType() != null
                && escalation.getRepeatType() != RepeatType.NONE
                && escalation.getNextRepeatAt() != null
                && escalation.getScheduledStartAt() != null
                && escalation.getScheduleTimezone() != null
                && escalation.getRepeatSourceId() == null;
    }
}
