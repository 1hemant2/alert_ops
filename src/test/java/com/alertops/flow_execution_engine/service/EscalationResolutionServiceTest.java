package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.repository.AuditEventRepository;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.dto.EscalationResolutionResponse;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.StepTimerRegistry;
import com.alertops.messaging.EscalationTimeoutService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;

class EscalationResolutionServiceTest {
    private static final UUID ESCALATION_ID = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("72000000-0000-0000-0000-000000000003");
    private static final UUID ACKNOWLEDGED_STEP_ID = UUID.fromString("72000000-0000-0000-0000-000000000004");
    private static final String RAW_TOKEN = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final Instant FIXED_NOW = Instant.parse("2026-10-07T12:00:00Z");

    private final EscalationRepository escalationRepository = org.mockito.Mockito.mock(EscalationRepository.class);
    private final FlowExecutionStateRepository stateRepository = org.mockito.Mockito.mock(FlowExecutionStateRepository.class);
    private final EscalationAcknowledgementTokenRepository tokenRepository =
            org.mockito.Mockito.mock(EscalationAcknowledgementTokenRepository.class);
    private final AuditEventRepository auditEventRepository =
            org.mockito.Mockito.mock(AuditEventRepository.class);
    private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
    private final StepTimerRegistry stepTimerRegistry = org.mockito.Mockito.mock(StepTimerRegistry.class);
    private final EscalationTimeoutService timeoutService =
            org.mockito.Mockito.mock(EscalationTimeoutService.class);
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final EscalationResolutionService service = new EscalationResolutionService(
            escalationRepository, stateRepository, tokenRepository, auditEventRepository,
            auditService, stepTimerRegistry, timeoutService, clock);

    private Escalation escalation;
    private FlowExecutionState acknowledgedStep;
    private EscalationAcknowledgementToken token;

    @BeforeEach
    void setUp() {
        AuthContextHolder.clear();

        escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setName("Database outage");
        escalation.setTeamId(TEAM_ID);
        escalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        escalation.setIssueSolvedBy("oncall@example.com");
        escalation.setAcknowledgedAt(FIXED_NOW.minus(Duration.ofMinutes(5)));
        escalation.setAcknowledgedStepId(ACKNOWLEDGED_STEP_ID);
        escalation.setResolutionDeadline(FIXED_NOW.plus(Duration.ofMinutes(10)));

        acknowledgedStep = new FlowExecutionState();
        acknowledgedStep.setId(ACKNOWLEDGED_STEP_ID);
        acknowledgedStep.setProcessId(ESCALATION_ID);
        acknowledgedStep.setUserEmail("oncall@example.com");
        acknowledgedStep.setStatus(FlowExecutionStepStatus.SENT);
        acknowledgedStep.setPosition(BigInteger.ONE);

        token = new EscalationAcknowledgementToken();
        token.setEscalationId(ESCALATION_ID);
        token.setExecutionStepId(ACKNOWLEDGED_STEP_ID);
        token.setRecipientEmail("oncall@example.com");
        token.setExpiresAt(FIXED_NOW.plus(Duration.ofHours(1)));
        token.setTokenHash("stored-hash");

        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));
        when(stateRepository.findUnsentStepsForUpdate(ESCALATION_ID)).thenReturn(List.of());
        when(stateRepository.findById(ACKNOWLEDGED_STEP_ID)).thenReturn(Optional.of(acknowledgedStep));
    }

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.clear();
    }

    @Test
    void teamMemberCanResolveAndClearsActiveAcknowledgementOwnership() {
        setTeamMemberContext("resolver@example.com");

        EscalationResolutionResponse result = service.resolveAsTeamMember(ESCALATION_ID);

        assertFalse(result.alreadyResolved());
        assertEquals(EscalationStatus.RESOLVED.name(), result.status());
        assertEquals("resolver@example.com", result.resolvedBy());
        assertEquals(FIXED_NOW, result.resolvedAt());
        assertEquals(EscalationStatus.RESOLVED, escalation.getStatus());
        assertNull(escalation.getResolutionType());
        assertEquals("resolver@example.com", escalation.getResolvedBy());
        assertEquals(FIXED_NOW, escalation.getResolvedAt());
        assertNull(escalation.getIssueSolvedBy());
        assertNull(escalation.getAcknowledgedAt());
        assertNull(escalation.getAcknowledgedStepId());
        assertNull(escalation.getResolutionDeadline());
        verify(escalationRepository).save(escalation);
        verify(stateRepository, never()).saveAll(any());
        verify(stepTimerRegistry, never()).cancel(any());
        verifyResolvedAudit(USER_ID, "resolver@example.com");
    }

    @Test
    void recipientTokenCanResolveTheCurrentAcknowledgementOwner() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        EscalationResolutionResponse result = service.resolveAsRecipient(RAW_TOKEN);

        assertFalse(result.alreadyResolved());
        assertEquals(EscalationStatus.RESOLVED.name(), result.status());
        assertEquals("oncall@example.com", escalation.getResolvedBy());
        assertEquals(FIXED_NOW, escalation.getResolvedAt());
        verifyResolvedAudit(null, "oncall@example.com");
    }

    @Test
    void resolutionSkipsAllUnsentStepsAndCancelsTheirWakeUpsAfterCommit() {
        setTeamMemberContext("resolver@example.com");
        FlowExecutionState pending = unsentStep(
                "72000000-0000-0000-0000-000000000005", FlowExecutionStepStatus.PENDING, 2);
        FlowExecutionState scheduled = unsentStep(
                "72000000-0000-0000-0000-000000000006", FlowExecutionStepStatus.SCHEDULED, 3);
        scheduled.setPublicationPending(true);
        scheduled.setDueAt(FIXED_NOW.plusSeconds(30));
        FlowExecutionState paused = unsentStep(
                "72000000-0000-0000-0000-000000000007", FlowExecutionStepStatus.PAUSED, 4);
        paused.setDueAt(FIXED_NOW.plus(Duration.ofMinutes(5)));
        when(stateRepository.findUnsentStepsForUpdate(ESCALATION_ID))
                .thenReturn(List.of(pending, scheduled, paused));
        when(stateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.resolveAsTeamMember(ESCALATION_ID);

        for (FlowExecutionState step : List.of(pending, scheduled, paused)) {
            assertEquals(FlowExecutionStepStatus.SKIPPED, step.getStatus());
            assertFalse(step.isPublicationPending());
            assertNull(step.getDueAt());
        }
        verify(stateRepository).saveAll(List.of(pending, scheduled, paused));
        verify(stepTimerRegistry).cancel(scheduled.getId());
        verify(stepTimerRegistry).cancel(paused.getId());
        verify(stepTimerRegistry, never()).cancel(pending.getId());
    }

    @Test
    void resolutionDoesNotCancelWakeUpsBeforeTheTransactionCommits() {
        setTeamMemberContext("resolver@example.com");
        FlowExecutionState scheduled = unsentStep(
                "72000000-0000-0000-0000-000000000008", FlowExecutionStepStatus.SCHEDULED, 2);
        when(stateRepository.findUnsentStepsForUpdate(ESCALATION_ID)).thenReturn(List.of(scheduled));
        when(stateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.resolveAsTeamMember(ESCALATION_ID);

            verify(stepTimerRegistry, never()).cancel(scheduled.getId());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            verify(stepTimerRegistry).cancel(scheduled.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void repeatedTeamResolutionReturnsSavedResultWithoutWritingAgain() {
        setTeamMemberContext("resolver@example.com");
        escalation.setStatus(EscalationStatus.RESOLVED);
        escalation.setResolvedBy("resolver@example.com");
        escalation.setResolvedAt(FIXED_NOW.minusSeconds(30));

        EscalationResolutionResponse result = service.resolveAsTeamMember(ESCALATION_ID);

        assertTrue(result.alreadyResolved());
        assertEquals("resolver@example.com", result.resolvedBy());
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(stateRepository, never()).findUnsentStepsForUpdate(any());
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void repeatedRecipientResolutionReturnsSavedResultWithoutReclaimingOwnership() {
        escalation.setStatus(EscalationStatus.RESOLVED);
        escalation.setResolvedBy("ONCALL@example.com ");
        escalation.setResolvedAt(FIXED_NOW.minusSeconds(30));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        AuditEventEntity resolutionAudit = org.mockito.Mockito.mock(AuditEventEntity.class);
        when(resolutionAudit.getAction()).thenReturn(AuditAction.RESOLVED.name());
        when(resolutionAudit.getUserEmail()).thenReturn("oncall@example.com");
        when(resolutionAudit.getMetadata()).thenReturn(
                "acknowledgedStepId=" + ACKNOWLEDGED_STEP_ID
                        + ";source=RECIPIENT_TOKEN;tokenHash=stored-hash");
        when(auditEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                "ESCALATION", ESCALATION_ID)).thenReturn(List.of(resolutionAudit));

        EscalationResolutionResponse result = service.resolveAsRecipient(RAW_TOKEN);

        assertTrue(result.alreadyResolved());
        verify(stateRepository, never()).findById(any());
        verify(stateRepository, never()).findUnsentStepsForUpdate(any());
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void repeatedRecipientResolutionRequiresTheExactAcceptedAcknowledgementStep() {
        escalation.setStatus(EscalationStatus.RESOLVED);
        escalation.setResolvedBy("oncall@example.com");
        escalation.setResolvedAt(FIXED_NOW.minusSeconds(30));
        token.setExecutionStepId(UUID.fromString("72000000-0000-0000-0000-000000000099"));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        AuditEventEntity resolutionAudit = org.mockito.Mockito.mock(AuditEventEntity.class);
        when(resolutionAudit.getAction()).thenReturn(AuditAction.RESOLVED.name());
        when(resolutionAudit.getUserEmail()).thenReturn("oncall@example.com");
        when(resolutionAudit.getMetadata()).thenReturn(
                "acknowledgedStepId=" + ACKNOWLEDGED_STEP_ID
                        + ";source=RECIPIENT_TOKEN;tokenHash=stored-hash");
        when(auditEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                "ESCALATION", ESCALATION_ID)).thenReturn(List.of(resolutionAudit));

        EscalationException error = assertThrows(
                EscalationException.class, () -> service.resolveAsRecipient(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(stateRepository, never()).findById(any());
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void repeatedRecipientResolutionRequiresTheExactAcceptedTokenHash() {
        escalation.setStatus(EscalationStatus.RESOLVED);
        escalation.setResolvedBy("oncall@example.com");
        escalation.setResolvedAt(FIXED_NOW.minusSeconds(30));
        token.setTokenHash("replacement-token-hash");
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        AuditEventEntity resolutionAudit = org.mockito.Mockito.mock(AuditEventEntity.class);
        when(resolutionAudit.getAction()).thenReturn(AuditAction.RESOLVED.name());
        when(resolutionAudit.getUserEmail()).thenReturn("oncall@example.com");
        when(resolutionAudit.getMetadata()).thenReturn(
                "acknowledgedStepId=" + ACKNOWLEDGED_STEP_ID
                        + ";source=RECIPIENT_TOKEN;tokenHash=stored-hash");
        when(auditEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                "ESCALATION", ESCALATION_ID)).thenReturn(List.of(resolutionAudit));

        EscalationException error = assertThrows(
                EscalationException.class, () -> service.resolveAsRecipient(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(stateRepository, never()).findById(any());
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void foreignTeamCannotResolveTheRun() {
        setTeamMemberContext("resolver@example.com", UUID.randomUUID());

        EscalationException error = assertThrows(
                EscalationException.class, () -> service.resolveAsTeamMember(ESCALATION_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void obsoleteRecipientTokenCannotResolveAfterOwnershipChanges() {
        token.setRecipientEmail("old-owner@example.com");
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        EscalationException error = assertThrows(
                EscalationException.class, () -> service.resolveAsRecipient(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void resolutionAtTheTimeoutIsRejected() {
        setTeamMemberContext("resolver@example.com");
        escalation.setResolutionDeadline(FIXED_NOW);

        EscalationException error = assertThrows(
                EscalationException.class, () -> service.resolveAsTeamMember(ESCALATION_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(stateRepository, never()).findUnsentStepsForUpdate(any());
    }

    @Test
    void terminalRunCannotBeResolvedAgainByAnotherTeamMember() {
        setTeamMemberContext("resolver@example.com");
        escalation.setStatus(EscalationStatus.COMPLETED);

        EscalationException error = assertThrows(
                EscalationException.class, () -> service.resolveAsTeamMember(ESCALATION_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void recipientResolutionUsesTheSavedTimeoutInsteadOfTokenTtl() {
        token.setExpiresAt(FIXED_NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        EscalationResolutionResponse result = service.resolveAsRecipient(RAW_TOKEN);

        assertFalse(result.alreadyResolved());
        assertEquals(EscalationStatus.RESOLVED.name(), result.status());
        verifyResolvedAudit(null, "oncall@example.com");
    }

    @Test
    void recipientResolutionRejectsATokenWithoutAnExpiry() {
        token.setExpiresAt(null);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.resolveAsRecipient(RAW_TOKEN));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    private void setTeamMemberContext(String email) {
        setTeamMemberContext(email, TEAM_ID);
    }

    private void setTeamMemberContext(String email, UUID teamId) {
        AuthContextHolder.set(new AuthContext(USER_ID, teamId, "TEAM_MEMBER", "jwt", email));
    }

    private FlowExecutionState unsentStep(String id, FlowExecutionStepStatus status, int position) {
        FlowExecutionState step = new FlowExecutionState();
        step.setId(UUID.fromString(id));
        step.setProcessId(ESCALATION_ID);
        step.setStatus(status);
        step.setPosition(BigInteger.valueOf(position));
        return step;
    }

    private void verifyResolvedAudit(UUID actorId, String email) {
        org.mockito.ArgumentCaptor<AuditEvent> auditEvent = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditEvent.capture());
        assertEquals(AuditAction.RESOLVED, auditEvent.getValue().action());
        assertEquals(actorId, auditEvent.getValue().userId());
        assertEquals(email, auditEvent.getValue().userEmail());
        assertEquals(EscalationStatus.ACKNOWLEDGED.name(), auditEvent.getValue().previousState());
        assertEquals(EscalationStatus.RESOLVED.name(), auditEvent.getValue().newState());
        assertEquals(
                "acknowledgedStepId=" + ACKNOWLEDGED_STEP_ID + ";source="
                        + (actorId == null ? "RECIPIENT_TOKEN;tokenHash=stored-hash" : "TEAM_MEMBER;tokenHash=none"),
                auditEvent.getValue().metadata());
    }
}
