package com.alertops.flow_execution_engine.service;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.flow_execution_engine.dto.ExecutionStateResponseDto;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.task.repository.TaskRepository;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.flow_execution_engine.dto.ScheduledEscalationRequest;
import com.alertops.flow_execution_engine.messaging.EscalationStartCancelled;
import com.alertops.flow_execution_engine.messaging.EscalationStartSchedule;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.exception.EscalationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class EscalationService {
   private EscalationRepository escalationRepository;
   private final FlowExecutionStateRepository flowExecutionStateRepository;
   private final FlowRepository flowRepository;
   private final TaskRepository taskRepository;
   private final ApplicationEventPublisher eventPublisher;
   private final Clock clock;
   private final AuditService auditService;

    public EscalationService(EscalationRepository escalationRepository,
                      FlowExecutionStateRepository flowExecutionStateRepository,
                      FlowRepository flowRepository,
                      TaskRepository taskRepository,
                      ApplicationEventPublisher eventPublisher,
                      Clock clock,
                      AuditService auditService) {
       this.escalationRepository = escalationRepository;
       this.flowExecutionStateRepository = flowExecutionStateRepository;
       this.flowRepository = flowRepository;
       this.taskRepository = taskRepository;
       this.eventPublisher = eventPublisher;
       this.clock = clock;
       this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    public Escalation createEscalation(String name, UUID taskId, UUID flowId) {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null) {
            throw EscalationException.unauthorized();
        }
        if (authContext.getTeamId() == null) {
            throw EscalationException.forbidden("Select a team before creating an escalation.");
        }
        return createEscalationForTeam(name, taskId, flowId, authContext.getTeamId());
    }

    @Transactional
    public Escalation createEscalationForTeam(
            String name, UUID taskId, UUID flowId, UUID teamId) {
        if (teamId == null
                || flowRepository.findByIdAndTeamId(flowId, teamId) == null
                || taskRepository.findById(taskId, teamId) == null) {
            throw EscalationException.invalidRequest("Task and flow must belong to the selected team");
        }

        Escalation escalation = new Escalation();
        escalation.setName(name);
        escalation.setTaskId(taskId);
        escalation.setFlowId(flowId);
        escalation.setTeamId(teamId);
        escalation.setResolutionType(null);
        escalation.setStatus(EscalationStatus.IDLE);
        return escalationRepository.save(escalation);
    }

    @Transactional
    public Escalation schedule(UUID escalationId, ScheduledEscalationRequest schedule) {
        AuthContext authContext = requireTeamContext();
        AuditActor actor = requireSchedulingActor(authContext);
        Escalation escalation = escalationRepository.findByIdAndTeamId(
                escalationId, authContext.getTeamId());
        if (escalation == null) {
            return null;
        }
        if (escalation.getStatus() != EscalationStatus.IDLE) {
            throw EscalationException.transitionConflict("Only an idle escalation can be scheduled");
        }
        EscalationStatus fromStatus = escalation.getStatus();

        Instant scheduledStartAt = resolveScheduledStart(schedule);
        String timezone = schedule.getTimezone().trim();
        int updated = escalationRepository.scheduleIdle(
                escalationId,
                authContext.getTeamId(),
                scheduledStartAt,
                timezone);
        if (updated != 1) {
            throw EscalationException.transitionConflict("Only an idle escalation can be scheduled");
        }
        escalation.setStatus(EscalationStatus.SCHEDULED);
        escalation.setScheduledStartAt(scheduledStartAt);
        escalation.setScheduleTimezone(timezone);
        escalation.setScheduledStartRetryCount(0);
        escalation.setScheduledStartNextRetryAt(null);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION, escalationId, AuditAction.SCHEDULED, fromStatus.name(),
                EscalationStatus.SCHEDULED.name(), actor.userId(), actor.email(), clock.instant(), null, null));
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new EscalationStartSchedule(escalationId, scheduledStartAt));
        }
        return escalation;
    }

    @Transactional
    public Escalation reschedule(UUID escalationId, ScheduledEscalationRequest schedule) {
        AuthContext authContext = requireTeamContext();
        AuditActor actor = requireSchedulingActor(authContext);
        Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, authContext.getTeamId());
        if (escalation == null) {
            return null;
        }
        if (escalation.getStatus() != EscalationStatus.SCHEDULED) {
            throw EscalationException.transitionConflict("Only scheduled escalations can be rescheduled");
        }
        EscalationStatus fromStatus = escalation.getStatus();
        Instant scheduledStartAt = resolveScheduledStart(schedule);
        int updated = escalationRepository.rescheduleScheduled(
                escalationId,
                authContext.getTeamId(),
                scheduledStartAt,
                schedule.getTimezone().trim());
        if (updated != 1) {
            throw EscalationException.transitionConflict("Only scheduled escalations can be rescheduled");
        }
        escalation.setScheduledStartAt(scheduledStartAt);
        escalation.setScheduleTimezone(schedule.getTimezone().trim());
        escalation.setScheduledStartRetryCount(0);
        escalation.setScheduledStartNextRetryAt(null);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION, escalationId, AuditAction.RESCHEDULED, fromStatus.name(),
                EscalationStatus.SCHEDULED.name(), actor.userId(), actor.email(), clock.instant(), null, null));
        Escalation saved = escalation;
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new EscalationStartSchedule(
                    saved.getId(), saved.getScheduledStartAt()));
        }
        return saved;
    }

    @Transactional
    public Escalation cancelScheduled(UUID escalationId) {
        AuthContext authContext = requireTeamContext();
        UUID cancelledByUserId = requireActorId(authContext);
        Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, authContext.getTeamId());
        if (escalation == null) {
            return null;
        }
        if (escalation.getStatus() == EscalationStatus.CANCELLED) {
            return escalation;
        }
        if (escalation.getStatus() != EscalationStatus.SCHEDULED) {
            throw EscalationException.transitionConflict("Only scheduled escalations can be cancelled");
        }
        Instant cancelledAt = clock.instant();
        int updated = escalationRepository.cancelScheduled(
                escalationId, authContext.getTeamId(), cancelledAt);
        if (updated != 1) {
            Escalation current = escalationRepository.findByIdAndTeamId(
                    escalationId, authContext.getTeamId());
            if (current != null && current.getStatus() == EscalationStatus.CANCELLED) {
                return current;
            }
            throw EscalationException.transitionConflict("Only scheduled escalations can be cancelled");
        }
        escalation.setStatus(EscalationStatus.CANCELLED);
        escalation.setCancelledAt(cancelledAt);
        escalation.setScheduledStartNextRetryAt(null);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION, escalationId, AuditAction.CANCELLED,
                EscalationStatus.SCHEDULED.name(), EscalationStatus.CANCELLED.name(),
                cancelledByUserId, null, cancelledAt, null, null));
        Escalation saved = escalation;
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new EscalationStartCancelled(saved.getId()));
        }
        return saved;
    }

    public List<Escalation> getEscalations(int page, int size, String sortBy, String sortDir) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            Set<String> allowedSortBy = Set.of("taskName", "createdAt");
            Set<String> allowedSortDir = Set.of("asc", "desc");

            if(page < 0) {
                page = 0;
            }

            String safeSortBy = sortBy == null ? "createdAt" : sortBy;
            if (!allowedSortBy.contains(safeSortBy)) {
                safeSortBy = "createdAt";
            }

            String safeSortDir = sortDir == null ? "asc" : sortDir;
            if (!allowedSortDir.contains(safeSortDir)) {
                safeSortDir = "asc";
            }

            Sort.Direction direction = "desc".equals(safeSortDir)
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Sort sort = Sort.by(direction, safeSortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Escalation> escalationPage = escalationRepository.findByTeamId(teamId, pageable);
            List<Escalation> escalations = escalationPage.getContent();

            return escalations;
        } catch (Exception e) {
            throw e;
        }
    }

    public Escalation getEscalationById(UUID escalationId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, teamId);

            if(escalation != null) {
                return escalation;
            } else {
                return null;
            }

        } catch (Exception e) {
            throw e;
        }
    }

    public List<ExecutionStateResponseDto> getExecutionStates(UUID escalationId) {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null || authContext.getTeamId() == null
                || escalationRepository.findByIdAndTeamId(escalationId, authContext.getTeamId()) == null) {
            return null;
        }
        return flowExecutionStateRepository.findAllByProcessIdOrderByPositionAsc(escalationId)
                .stream()
                .map(ExecutionStateResponseDto::from)
                .toList();
    }

    private AuthContext requireTeamContext() {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null) {
            throw EscalationException.unauthorized();
        }
        if (authContext.getTeamId() == null) {
            throw EscalationException.forbidden("Select a team before using this escalation action.");
        }
        return authContext;
    }

    private AuditActor requireSchedulingActor(AuthContext authContext) {
        if (authContext == null) {
            throw EscalationException.unauthorized();
        }
        UUID userId = requireActorId(authContext);
        String email = authContext.getEmail();
        if (email == null || email.isBlank()) {
            throw EscalationException.unauthorized();
        }
        return new AuditActor(userId, email.trim());
    }

    private UUID requireActorId(AuthContext authContext) {
        UUID userId = authContext == null ? null : authContext.getUserId();
        if (userId == null) {
            throw EscalationException.unauthorized();
        }
        return userId;
    }

    private Instant resolveScheduledStart(ScheduledEscalationRequest schedule) {
        // A date and time by themselves are not enough to identify one real moment.
        // For example, "10:00" could mean 10:00 in India, London, or New York.
        if (schedule == null || schedule.getScheduleDate() == null
                || schedule.getScheduleTime() == null || schedule.getTimezone() == null
                || schedule.getTimezone().isBlank()) {
            throw EscalationException.invalidRequest(
                    "A scheduled escalation requires date, time, and timezone");
        }

        // Convert the user's timezone text (for example, "Asia/Kolkata") into
        // Java's timezone rules, including daylight-saving-time rules where relevant.
        ZoneId zone;
        try {
            zone = ZoneId.of(schedule.getTimezone().trim());
        } catch (RuntimeException e) {
            throw EscalationException.invalidRequest("Timezone must be a valid IANA timezone");
        }

        // Combine the separate calendar date and wall-clock time entered by the user.
        LocalDateTime local = LocalDateTime.of(schedule.getScheduleDate(), schedule.getScheduleTime());

        // A normal local time has exactly one UTC offset. During daylight-saving changes:
        // - 0 offsets means the clock jumped over this local time, so it does not exist.
        // - 2 offsets means the clock repeated this local time, so it is ambiguous.
        // Reject both cases instead of silently choosing the wrong instant.
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) {
            throw EscalationException.invalidRequest(
                    "The selected local time is ambiguous or does not exist in this timezone");
        }

        // Apply the timezone's one valid offset to get an absolute UTC moment.
        // This Instant is what we persist and give to the scheduler; it is independent
        // of the server's local timezone. Example: 10:00 Asia/Kolkata = 04:30 UTC.
        Instant start = local.toInstant(offsets.get(0));

        // Scheduling in the past (or exactly now) is rejected. The injected Clock keeps
        // this comparison testable and ensures all scheduling decisions use one time source.
        if (!start.isAfter(clock.instant())) {
            throw EscalationException.invalidRequest("Scheduled start time must be in the future");
        }
        return start;
    }

    private record AuditActor(UUID userId, String email) {
    }

}
