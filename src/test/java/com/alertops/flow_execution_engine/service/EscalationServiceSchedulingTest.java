package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.alertops.flow.model.Flow;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow_execution_engine.dto.ScheduledEscalationRequest;
import com.alertops.flow_execution_engine.messaging.EscalationStartSchedule;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.interfaces.TaskView;
import com.alertops.task.repository.TaskRepository;

class EscalationServiceSchedulingTest {
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final FlowExecutionStateRepository states = mock(FlowExecutionStateRepository.class);
    private final FlowRepository flows = mock(FlowRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final EscalationService service = new EscalationService(
            escalations, states, flows, tasks, events, Clock.fixed(now, ZoneOffset.UTC), auditService);

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void scheduledStartStoresUtcInstantAndTimezone() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(actorId, teamId, "TEAM_OWNER", "token", "owner@example.com"));
        when(flows.findByIdAndTeamId(flowId, teamId)).thenReturn(new Flow());
        when(tasks.findById(taskId, teamId)).thenReturn(mock(TaskView.class));
        when(escalations.save(any(Escalation.class))).thenAnswer(invocation -> {
            Escalation saved = invocation.getArgument(0);
            saved.setId(escalationId);
            return saved;
        });
        Escalation scheduled = new Escalation();
        scheduled.setId(escalationId);
        scheduled.setTeamId(teamId);
        scheduled.setStatus(EscalationStatus.IDLE);
        when(escalations.findByIdAndTeamId(escalationId, teamId)).thenReturn(scheduled);
        when(escalations.scheduleIdle(any(), any(), any(), any())).thenReturn(1);

        ScheduledEscalationRequest schedule = new ScheduledEscalationRequest();
        schedule.setScheduleDate(LocalDate.of(2026, 1, 1));
        schedule.setScheduleTime(LocalTime.of(10, 0));
        schedule.setTimezone("Asia/Kolkata");

        service.createEscalationForTeam("Database outage", taskId, flowId, teamId);
        Escalation result = service.schedule(escalationId, schedule);

        assertEquals(EscalationStatus.SCHEDULED, result.getStatus());
        assertEquals(Instant.parse("2026-01-01T04:30:00Z"), result.getScheduledStartAt());
        assertEquals("Asia/Kolkata", result.getScheduleTimezone());
        verify(events).publishEvent(new EscalationStartSchedule(escalationId, result.getScheduledStartAt()));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditEntityType.ESCALATION, audit.getValue().entityType());
        assertEquals(AuditAction.SCHEDULED, audit.getValue().action());
        assertEquals(EscalationStatus.IDLE.name(), audit.getValue().previousState());
        assertEquals(EscalationStatus.SCHEDULED.name(), audit.getValue().newState());
        assertEquals(actorId, audit.getValue().userId());
        assertEquals("owner@example.com", audit.getValue().userEmail());
    }

    @Test
    void cancellationWritesAnAuditEventForTheActorWhoCancelledTheSchedule() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(actorId, teamId, "TEAM_OWNER", "token", "owner@example.com"));

        Escalation scheduled = new Escalation();
        scheduled.setId(escalationId);
        scheduled.setTeamId(teamId);
        scheduled.setStatus(EscalationStatus.SCHEDULED);
        when(escalations.findByIdAndTeamId(escalationId, teamId)).thenReturn(scheduled);
        when(escalations.cancelScheduled(any(), any(), any())).thenReturn(1);

        Escalation result = service.cancelScheduled(escalationId);

        assertEquals(EscalationStatus.CANCELLED, result.getStatus());
        assertEquals(now, result.getCancelledAt());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditAction.CANCELLED, audit.getValue().action());
        assertEquals(actorId, audit.getValue().userId());
    }

    @Test
    void reschedulingWritesTheLatestSchedulingActorToAudit() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID originalActorId = UUID.randomUUID();
        UUID latestActorId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                originalActorId, teamId, "TEAM_OWNER", "token", "original@example.com"));

        Escalation scheduled = new Escalation();
        scheduled.setId(escalationId);
        scheduled.setTeamId(teamId);
        scheduled.setStatus(EscalationStatus.SCHEDULED);
        when(escalations.findByIdAndTeamId(escalationId, teamId)).thenReturn(scheduled);
        when(escalations.rescheduleScheduled(any(), any(), any(), any())).thenReturn(1);

        AuthContextHolder.set(new AuthContext(
                latestActorId, teamId, "TEAM_OWNER", "token", "latest@example.com"));
        ScheduledEscalationRequest reschedule = new ScheduledEscalationRequest();
        reschedule.setScheduleDate(LocalDate.of(2026, 1, 2));
        reschedule.setScheduleTime(LocalTime.of(10, 0));
        reschedule.setTimezone("Asia/Kolkata");

        Escalation result = service.reschedule(escalationId, reschedule);

        assertEquals(EscalationStatus.SCHEDULED, result.getStatus());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditAction.RESCHEDULED, audit.getValue().action());
        assertEquals(latestActorId, audit.getValue().userId());
        assertEquals("latest@example.com", audit.getValue().userEmail());
    }

    @Test
    void scheduledStartMustBeFutureAndUseAnUnambiguousLocalTime() {
        UUID teamId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Escalation idle = new Escalation();
        idle.setStatus(EscalationStatus.IDLE);
        when(escalations.findByIdAndTeamId(any(), any())).thenReturn(idle);
        ScheduledEscalationRequest inThePast = new ScheduledEscalationRequest();
        inThePast.setScheduleDate(LocalDate.of(2025, 12, 31));
        inThePast.setScheduleTime(LocalTime.NOON);
        inThePast.setTimezone("UTC");

        assertThrows(IllegalArgumentException.class, () -> service.schedule(UUID.randomUUID(), inThePast));

        ScheduledEscalationRequest daylightSavingGap = new ScheduledEscalationRequest();
        daylightSavingGap.setScheduleDate(LocalDate.of(2026, 3, 8));
        daylightSavingGap.setScheduleTime(LocalTime.of(2, 30));
        daylightSavingGap.setTimezone("America/New_York");

        assertThrows(IllegalArgumentException.class, () -> service.schedule(UUID.randomUUID(), daylightSavingGap));
    }
}
