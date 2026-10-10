package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.messaging.EscalationRepeatSchedule;
import com.alertops.flow_execution_engine.messaging.EscalationStartSchedule;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.RepeatType;
import com.alertops.flow_execution_engine.repository.EscalationRepository;

class EscalationRepeatServiceTest {
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Instant now = Instant.parse("2026-01-05T12:00:00Z");
    private final EscalationRepeatService service = new EscalationRepeatService(
            escalations,
            auditService,
            events,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    // Verifies one child is created while the original advances past downtime.
    void createsLatestMissedChildAndAdvancesOriginal() {
        UUID sourceId = UUID.randomUUID();
        Escalation original = repeatingOriginal(sourceId);
        when(escalations.findByIdForUpdate(sourceId)).thenReturn(Optional.of(original));
        when(escalations.save(any(Escalation.class))).thenAnswer(invocation -> {
            Escalation saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        Optional<Escalation> result = service.createLatestDueRun(sourceId, now);

        assertTrue(result.isPresent());
        Escalation child = result.get();
        assertEquals(EscalationStatus.SCHEDULED, child.getStatus());
        assertEquals(RepeatType.NONE, child.getRepeatType());
        assertEquals(sourceId, child.getRepeatSourceId());
        assertEquals(Instant.parse("2026-01-05T10:00:00Z"), child.getScheduledStartAt());
        assertEquals(Instant.parse("2026-01-06T10:00:00Z"), original.getNextRepeatAt());
        verify(events).publishEvent(new EscalationStartSchedule(child.getId(), child.getScheduledStartAt()));
        verify(events).publishEvent(new EscalationRepeatSchedule(sourceId, original.getNextRepeatAt()));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditAction.REPEAT_CREATED, audit.getValue().action());
    }

    @Test
    // Verifies an original without an enabled repeat does not create a child.
    void ignoresAStoppedOriginal() {
        Escalation original = new Escalation();
        original.setId(UUID.randomUUID());
        original.setRepeatType(RepeatType.DAILY);
        original.setNextRepeatAt(null);
        when(escalations.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        clearInvocations(auditService, events);

        assertTrue(service.createLatestDueRun(original.getId(), now).isEmpty());

        verifyNoInteractions(auditService, events);
    }

    private Escalation repeatingOriginal(UUID id) {
        Escalation original = new Escalation();
        original.setId(id);
        original.setName("Daily backup check");
        original.setStatus(EscalationStatus.COMPLETED);
        original.setTaskId(UUID.randomUUID());
        original.setFlowId(UUID.randomUUID());
        original.setTeamId(UUID.randomUUID());
        original.setScheduledStartAt(Instant.parse("2026-01-01T10:00:00Z"));
        original.setScheduleTimezone("UTC");
        original.setRepeatType(RepeatType.DAILY);
        original.setNextRepeatAt(Instant.parse("2026-01-02T10:00:00Z"));
        original.setScheduledByUserId(UUID.randomUUID());
        original.setScheduledByUserEmail("owner@example.com");
        return original;
    }
}
