package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.alertops.audit.model.AuditActorType;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.repository.AuditEventRepository;
import com.alertops.flow_execution_engine.dto.EscalationHistoryPageResponse;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;

class EscalationHistoryServiceTest {
    private final EscalationRepository escalationRepository = mock(EscalationRepository.class);
    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
    private final EscalationHistoryService service = new EscalationHistoryService(
            escalationRepository, auditEventRepository);

    @AfterEach
    // Clears the request team context between history access tests.
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    // Verifies that authorized history is paged, ordered, and safely mapped.
    void returnsOrderedHistoryAndRemovesSensitiveDetails() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "jwt", "owner@example.com"));
        when(escalationRepository.findByIdAndTeamId(escalationId, teamId)).thenReturn(new Escalation());

        AuditEventEntity userEvent = auditEvent(
                firstEventId,
                "ACKNOWLEDGED",
                "owner@example.com",
                UUID.randomUUID(),
                "executionStepId=" + UUID.randomUUID()
                        + ";attempt=1;tokenHash=do-not-return;recipientEmail=oncall@example.com",
                "ACKNOWLEDGED");
        AuditEventEntity systemEvent = auditEvent(
                secondEventId,
                "NOTIFICATION_SENT",
                null,
                null,
                "executionStepId=" + UUID.randomUUID() + ";attempt=2",
                null);
        when(auditEventRepository.findAllByEntityTypeAndEntityId(
                any(String.class), any(UUID.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(userEvent, systemEvent), invocation.getArgument(2), 4));

        EscalationHistoryPageResponse result = service.getHistory(escalationId, 1, 2);

        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(4, result.totalEvents());
        assertEquals(2, result.totalPages());
        assertEquals(AuditActorType.USER, result.events().get(0).actorType());
        assertEquals(AuditActorType.SYSTEM, result.events().get(1).actorType());
        assertTrue(result.events().get(0).details().containsKey("attempt"));
        assertTrue(!result.events().get(0).details().containsKey("tokenHash"));
        assertEquals("ACKNOWLEDGED", result.events().get(0).reason());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findAllByEntityTypeAndEntityId(
                any(String.class), any(UUID.class), page.capture());
        assertEquals(List.of("occurredAt", "id"), page.getValue().getSort().stream()
                .map(order -> order.getProperty()).toList());
    }

    @Test
    // Verifies that a foreign-team escalation cannot reveal its activity history.
    void rejectsHistoryForAnotherTeam() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "jwt", "owner@example.com"));
        when(escalationRepository.findByIdAndTeamId(escalationId, teamId)).thenReturn(null);

        EscalationException exception = assertThrows(
                EscalationException.class, () -> service.getHistory(escalationId, 0, 20));

        assertEquals(404, exception.getStatus().value());
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    // Verifies that anonymous history reads are rejected before any repository lookup.
    void rejectsAnonymousHistoryRead() {
        EscalationException exception = assertThrows(
                EscalationException.class, () -> service.getHistory(UUID.randomUUID(), 0, 20));

        assertEquals(401, exception.getStatus().value());
        verifyNoInteractions(escalationRepository, auditEventRepository);
    }

    @Test
    // Verifies that invalid history page sizes are rejected at the service boundary.
    void rejectsInvalidHistoryPage() {
        EscalationException exception = assertThrows(
                EscalationException.class, () -> service.getHistory(UUID.randomUUID(), 0, 101));

        assertEquals(400, exception.getStatus().value());
        verify(escalationRepository, never()).findByIdAndTeamId(any(), any());
    }

    // Builds one persisted audit row for history mapping tests.
    private AuditEventEntity auditEvent(
            UUID id,
            String action,
            String actorEmail,
            UUID actorId,
            String metadata,
            String reason) {
        AuditEventEntity event = mock(AuditEventEntity.class);
        when(event.getId()).thenReturn(id);
        when(event.getAction()).thenReturn(action);
        when(event.getPreviousState()).thenReturn(null);
        when(event.getNewState()).thenReturn(action);
        when(event.getUserEmail()).thenReturn(actorEmail);
        when(event.getUserId()).thenReturn(actorId);
        when(event.getOccurredAt()).thenReturn(Instant.parse("2026-10-08T12:00:00Z"));
        when(event.getReason()).thenReturn(reason);
        when(event.getMetadata()).thenReturn(metadata);
        return event;
    }
}
