package com.alertops.flow_execution_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;
import com.alertops.flow_execution_engine.model.EscalationActionCapability;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.StepTimerRegistry;
import com.alertops.messaging.EscalationTimeoutService;

class EscalationAcknowledgementServiceTest {
    private static final UUID ESCALATION_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final String RAW_TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Instant FIXED_NOW = Instant.parse("2026-10-06T12:00:00Z");

    private final EscalationAcknowledgementTokenRepository tokenRepository = org.mockito.Mockito.mock(EscalationAcknowledgementTokenRepository.class);
    private final EscalationRepository escalationRepository = org.mockito.Mockito.mock(EscalationRepository.class);
    private final FlowExecutionStateRepository stateRepository = org.mockito.Mockito.mock(FlowExecutionStateRepository.class);
    private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
    private final StepTimerRegistry stepTimerRegistry = org.mockito.Mockito.mock(StepTimerRegistry.class);
    private final EscalationTimeoutService timeoutService =
            org.mockito.Mockito.mock(EscalationTimeoutService.class);
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final EscalationAcknowledgementService service = new EscalationAcknowledgementService(
            tokenRepository, escalationRepository, stateRepository,
            auditService, stepTimerRegistry, timeoutService, clock,
            Duration.ofHours(72), "https://alerts.example.com/");

    private Escalation escalation;
    private EscalationAcknowledgementToken token;
    private FlowExecutionState executionStep;

    @BeforeEach
    // Creates an open run with a valid sent acknowledgement step.
    void setUp() {
        escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setName("Database outage");
        escalation.setStatus(EscalationStatus.OPEN);

        token = new EscalationAcknowledgementToken();
        token.setEscalationId(ESCALATION_ID);
        executionStep = new FlowExecutionState();
        executionStep.setId(UUID.fromString("71000000-0000-0000-0000-000000000002"));
        executionStep.setProcessId(ESCALATION_ID);
        executionStep.setUserEmail("oncall@example.com");
        executionStep.setStatus(FlowExecutionStepStatus.SENT);
        executionStep.setDueAt(FIXED_NOW.plus(Duration.ofMinutes(5)));
        executionStep.setPosition(java.math.BigInteger.ONE);
        token.setExecutionStepId(executionStep.getId());
        token.setRecipientEmail("oncall@example.com");
        token.setTokenHash("hash-is-looked-up-by-repository");
        token.setExpiresAt(FIXED_NOW.plus(Duration.ofHours(1)));
        when(stateRepository.findById(executionStep.getId())).thenReturn(Optional.of(executionStep));
        when(stateRepository.findTopByProcessIdAndStatusOrderByPositionDesc(
                ESCALATION_ID, FlowExecutionStepStatus.SENT)).thenReturn(executionStep);
    }

    @Test
    void createsRecipientScopedExpiringLinkAndStoresOnlyItsHash() {
        String link = service.createAcknowledgementUrl(escalation, executionStep);

        assertTrue(link.startsWith("https://alerts.example.com/acknowledge?token="));
        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        assertEquals(43, rawToken.length());
        org.mockito.ArgumentCaptor<EscalationAcknowledgementToken> savedToken =
                org.mockito.ArgumentCaptor.forClass(EscalationAcknowledgementToken.class);
        verify(tokenRepository).save(savedToken.capture());
        assertEquals(ESCALATION_ID, savedToken.getValue().getEscalationId());
        assertEquals(executionStep.getId(), savedToken.getValue().getExecutionStepId());
        assertEquals("oncall@example.com", savedToken.getValue().getRecipientEmail());
        assertNotEquals(rawToken, savedToken.getValue().getTokenHash());
        assertEquals(64, savedToken.getValue().getTokenHash().length());
        assertTrue(savedToken.getValue().getExpiresAt().isAfter(savedToken.getValue().getCreatedAt()));
    }

    @Test
    // Binds an Escalate now link to its exact source and next target step.
    void createsExplicitEscalateNowCapabilityForTheNextStep() {
        FlowExecutionState nextStep = new FlowExecutionState();
        nextStep.setId(UUID.fromString("71000000-0000-0000-0000-000000000005"));
        nextStep.setProcessId(ESCALATION_ID);
        nextStep.setUserEmail("next@example.com");

        String link = service.createEscalateNowUrl(escalation, executionStep, nextStep);

        assertTrue(link.startsWith("https://alerts.example.com/escalate?token="));
        org.mockito.ArgumentCaptor<EscalationAcknowledgementToken> savedToken =
                org.mockito.ArgumentCaptor.forClass(EscalationAcknowledgementToken.class);
        verify(tokenRepository).save(savedToken.capture());
        assertEquals(EscalationActionCapability.ESCALATE_NOW, savedToken.getValue().getCapability());
        assertEquals(executionStep.getId(), savedToken.getValue().getExecutionStepId());
        assertEquals(nextStep.getId(), savedToken.getValue().getExpectedTargetStepId());
    }

    @Test
    void previewDoesNotChangeTheRunAndConfirmRecordsAcknowledgement() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(escalation));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        var preview = service.preview(RAW_TOKEN);
        assertEquals("Database outage", preview.escalationName());
        assertEquals("oncall@example.com", preview.recipientEmail());
        assertFalse(preview.alreadyAcknowledged());
        verify(escalationRepository, never()).save(any(Escalation.class));

        var result = service.acknowledge(RAW_TOKEN);
        assertEquals(EscalationStatus.COMPLETED, escalation.getStatus());
        assertEquals(EscalationResolutionType.ACKNOWLEDGED, escalation.getResolutionType());
        assertEquals("oncall@example.com", escalation.getIssueSolvedBy());
        assertNotNull(escalation.getAcknowledgedAt());
        assertTrue(result.alreadyAcknowledged());
        verify(escalationRepository).findByIdForUpdate(ESCALATION_ID);
        verify(stateRepository).markUnsentStepsSkipped(ESCALATION_ID);
        verify(escalationRepository).save(escalation);
    }

    @Test
    void sameRecipientStepsReceiveDistinctStepBoundTokens() {
        FlowExecutionState secondStep = new FlowExecutionState();
        secondStep.setId(UUID.fromString("71000000-0000-0000-0000-000000000003"));
        secondStep.setProcessId(ESCALATION_ID);
        secondStep.setUserEmail("oncall@example.com");

        service.createAcknowledgementUrl(escalation, executionStep);
        service.createAcknowledgementUrl(escalation, secondStep);

        org.mockito.ArgumentCaptor<EscalationAcknowledgementToken> savedTokens =
                org.mockito.ArgumentCaptor.forClass(EscalationAcknowledgementToken.class);
        verify(tokenRepository, org.mockito.Mockito.times(2)).save(savedTokens.capture());
        assertNotEquals(savedTokens.getAllValues().get(0).getExecutionStepId(),
                savedTokens.getAllValues().get(1).getExecutionStepId());
    }

    @Test
    void repeatedAcknowledgementReturnsTheSavedResult() {
        escalation.setStatus(EscalationStatus.COMPLETED);
        escalation.setResolutionType(EscalationResolutionType.ACKNOWLEDGED);
        escalation.setIssueSolvedBy("ONCALL@example.com ");
        escalation.setAcknowledgedAt(FIXED_NOW.minusSeconds(30));
        token.setExpiresAt(FIXED_NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        var result = service.acknowledge(RAW_TOKEN);

        assertTrue(result.alreadyAcknowledged());
        assertEquals(escalation.getAcknowledgedAt(), result.acknowledgedAt());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void expiredLinkCannotAcknowledgeAnActiveRun() {
        token.setExpiresAt(FIXED_NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.GONE, error.getStatusCode());
        assertEquals(EscalationStatus.OPEN, escalation.getStatus());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    // Verifies that the shared acknowledgement timeout rejects a valid token.
    void expiredAcknowledgementWindowRejectsValidToken() {
        executionStep.setDueAt(FIXED_NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.GONE, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(timeoutService, never()).scheduleResolutionTimeout(any(), any(), any());
    }

    @Test
    void unknownTokenCannotAcknowledgeARun() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(escalationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    // Prevents a manual-action token from being reused as an acknowledgement.
    void escalateNowTokenCannotAcknowledgeARun() {
        token.setCapability(EscalationActionCapability.ESCALATE_NOW);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(escalationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void finishedRunCannotBeChangedByAnUnusedToken() {
        escalation.setStatus(EscalationStatus.COMPLETED);
        escalation.setResolutionType(EscalationResolutionType.EXHAUSTED);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void aTokenCannotBeUsedByAnotherRecipientAfterAcknowledgement() {
        escalation.setStatus(EscalationStatus.COMPLETED);
        escalation.setResolutionType(EscalationResolutionType.ACKNOWLEDGED);
        escalation.setIssueSolvedBy("another@example.com");
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void tokenBoundToAnotherRunCannotBeUsedEvenForTheSameRecipient() {
        FlowExecutionState wrongStep = new FlowExecutionState();
        wrongStep.setId(executionStep.getId());
        wrongStep.setProcessId(UUID.randomUUID());
        wrongStep.setUserEmail("oncall@example.com");
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));
        when(stateRepository.findById(executionStep.getId())).thenReturn(Optional.of(wrongStep));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void enabledAcknowledgementPausesNextUnsentStepAndStoresResolutionTimeout() {
        executionStep.setResolutionTimeoutEnabled(true);
        executionStep.setResolutionTimeout(Duration.ofMinutes(10));
        FlowExecutionState nextStep = new FlowExecutionState();
        nextStep.setId(UUID.fromString("71000000-0000-0000-0000-000000000004"));
        nextStep.setProcessId(ESCALATION_ID);
        nextStep.setStatus(FlowExecutionStepStatus.SCHEDULED);
        nextStep.setPosition(java.math.BigInteger.valueOf(2));
        nextStep.setPublicationPending(true);
        nextStep.setDueAt(FIXED_NOW.plus(Duration.ofMinutes(5)));
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID, List.of(FlowExecutionStepStatus.PENDING, FlowExecutionStepStatus.SCHEDULED)))
                .thenReturn(nextStep);
        when(stateRepository.save(nextStep)).thenReturn(nextStep);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        var result = service.acknowledge(RAW_TOKEN);

        assertTrue(result.alreadyAcknowledged());
        assertEquals(EscalationStatus.ACKNOWLEDGED, escalation.getStatus());
        assertEquals(null, escalation.getResolutionType());
        assertEquals(executionStep.getId(), escalation.getAcknowledgedStepId());
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(10)), escalation.getResolutionDeadline());
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(10)), result.resolutionDeadline());
        assertEquals("oncall@example.com", escalation.getIssueSolvedBy());
        assertEquals(FlowExecutionStepStatus.PAUSED, nextStep.getStatus());
        assertFalse(nextStep.isPublicationPending());
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(5)), nextStep.getDueAt());
        verify(stateRepository).save(nextStep);
        verify(escalationRepository).save(escalation);
        verify(stateRepository, never()).markUnsentStepsSkipped(ESCALATION_ID);
        verify(stepTimerRegistry).cancel(nextStep.getId());

        org.mockito.ArgumentCaptor<AuditEvent> auditEvent = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditEvent.capture());
        assertEquals(AuditAction.ACKNOWLEDGED, auditEvent.getValue().action());
        assertEquals(EscalationStatus.ACKNOWLEDGED.name(), auditEvent.getValue().newState());
        assertTrue(auditEvent.getValue().metadata().contains("executionStepId=" + executionStep.getId()));
    }

    @Test
    void enabledAcknowledgementMarksFinalSentStepActiveWithoutAFollowingStep() {
        executionStep.setResolutionTimeoutEnabled(true);
        executionStep.setResolutionTimeout(Duration.ofMinutes(10));
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID, List.of(FlowExecutionStepStatus.PENDING, FlowExecutionStepStatus.SCHEDULED)))
                .thenReturn(null);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        service.acknowledge(RAW_TOKEN);

        assertEquals(EscalationStatus.ACKNOWLEDGED, escalation.getStatus());
        assertEquals(executionStep.getId(), escalation.getAcknowledgedStepId());
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(10)), escalation.getResolutionDeadline());
        verify(stepTimerRegistry, never()).cancel(any());
    }

    @Test
    void repeatedActiveAcknowledgementDoesNotExtendSavedResolutionTimeout() {
        Instant originalAcknowledgedAt = FIXED_NOW.minus(Duration.ofMinutes(5));
        Instant originalDeadline = FIXED_NOW.plus(Duration.ofMinutes(5));
        escalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        escalation.setIssueSolvedBy("ONCALL@example.com ");
        escalation.setAcknowledgedStepId(executionStep.getId());
        escalation.setAcknowledgedAt(originalAcknowledgedAt);
        escalation.setResolutionDeadline(originalDeadline);
        token.setExpiresAt(FIXED_NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        var result = service.acknowledge(RAW_TOKEN);

        assertTrue(result.alreadyAcknowledged());
        assertEquals(originalAcknowledgedAt, escalation.getAcknowledgedAt());
        assertEquals(originalDeadline, escalation.getResolutionDeadline());
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(stateRepository, never()).save(any(FlowExecutionState.class));
        verify(auditService, never()).record(any(AuditEvent.class));
        verify(stepTimerRegistry, never()).cancel(any());
    }

    @Test
    void acknowledgementFromAnOlderSentStepCannotBypassTheCurrentStep() {
        FlowExecutionState newerSentStep = new FlowExecutionState();
        newerSentStep.setId(UUID.fromString("71000000-0000-0000-0000-000000000005"));
        newerSentStep.setProcessId(ESCALATION_ID);
        newerSentStep.setStatus(FlowExecutionStepStatus.SENT);
        newerSentStep.setPosition(java.math.BigInteger.valueOf(2));
        when(stateRepository.findTopByProcessIdAndStatusOrderByPositionDesc(
                ESCALATION_ID, FlowExecutionStepStatus.SENT)).thenReturn(newerSentStep);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.acknowledge(RAW_TOKEN));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(auditService, never()).record(any(AuditEvent.class));
    }
}
