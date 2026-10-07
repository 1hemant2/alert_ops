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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.repository.AuditEventRepository;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.dto.EscalationManualActionRequest;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;
import com.alertops.flow_execution_engine.model.EscalationActionCapability;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.EscalationTimeoutService;
import com.alertops.messaging.StepSchedulingService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;

class EscalationManualActionServiceTest {
    private static final UUID ESCALATION_ID = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("73000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("73000000-0000-0000-0000-000000000003");
    private static final UUID SOURCE_ID = UUID.fromString("73000000-0000-0000-0000-000000000004");
    private static final UUID TARGET_ID = UUID.fromString("73000000-0000-0000-0000-000000000005");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final String RAW_TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final EscalationRepository escalationRepository = org.mockito.Mockito.mock(EscalationRepository.class);
    private final FlowExecutionStateRepository stateRepository = org.mockito.Mockito.mock(FlowExecutionStateRepository.class);
    private final EscalationAcknowledgementTokenRepository tokenRepository =
            org.mockito.Mockito.mock(EscalationAcknowledgementTokenRepository.class);
    private final AuditEventRepository auditEventRepository = org.mockito.Mockito.mock(AuditEventRepository.class);
    private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
    private final StepSchedulingService stepSchedulingService = org.mockito.Mockito.mock(StepSchedulingService.class);
    private final EscalationTimeoutService timeoutService = org.mockito.Mockito.mock(EscalationTimeoutService.class);
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final EscalationManualActionService service = new EscalationManualActionService(
            escalationRepository,
            stateRepository,
            tokenRepository,
            auditEventRepository,
            auditService,
            stepSchedulingService,
            timeoutService,
            clock);

    private Escalation escalation;
    private FlowExecutionState source;
    private FlowExecutionState target;

    @BeforeEach
    // Creates an open run with one current sent step and one waiting next step.
    void setUp() {
        escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setTeamId(TEAM_ID);
        escalation.setName("Database outage");
        escalation.setStatus(EscalationStatus.OPEN);

        source = step(SOURCE_ID, FlowExecutionStepStatus.SENT, 1, "first@example.com");
        source.setDueAt(FIXED_NOW.plus(Duration.ofMinutes(5)));
        target = step(TARGET_ID, FlowExecutionStepStatus.PENDING, 2, "second@example.com");

        when(auditEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                "ESCALATION", ESCALATION_ID)).thenReturn(List.of());
        when(stateRepository.findAllByProcessIdOrderByPositionAsc(ESCALATION_ID))
                .thenReturn(List.of(source, target));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(escalation));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(escalation));

        AuthContextHolder.set(new AuthContext(ACTOR_ID, TEAM_ID, "USER", "jwt", "member@example.com"));
    }

    @AfterEach
    // Clears the request actor so one test cannot authorize another test.
    void clearAuthentication() {
        AuthContextHolder.clear();
    }

    @Test
    // Shows the next recipient without scheduling or changing the run.
    void previewsTheExpectedNextRecipientWithoutChangingState() {
        var result = service.previewAsTeamMember(
                ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID));

        assertTrue(result.actionAvailable());
        assertEquals("second@example.com", result.targetRecipientEmail());
        assertEquals(SOURCE_ID, result.sourceStepId());
        assertEquals(TARGET_ID, result.targetStepId());
        verify(stepSchedulingService, never()).scheduleStepImmediately(any(), any());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    // Makes an open escalation's exact next step due immediately and records its actor.
    void escalatesAnOpenRunToTheExpectedNextStep() {
        when(stateRepository.findTopByProcessIdAndStatusOrderByPositionDesc(
                ESCALATION_ID, FlowExecutionStepStatus.SENT)).thenReturn(source);
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID, List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED))).thenReturn(target);

        var result = service.escalateNowAsTeamMember(
                ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID));

        assertTrue(result.alreadyEscalated());
        assertEquals(EscalationStatus.OPEN.name(), result.status());
        verify(stepSchedulingService).scheduleStepImmediately(target, FIXED_NOW);
        verify(timeoutService, never()).cancelResolutionTimeout(ESCALATION_ID);
        verify(escalationRepository).save(escalation);

        org.mockito.ArgumentCaptor<AuditEvent> event = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals(AuditAction.ESCALATED_NOW, event.getValue().action());
        assertTrue(event.getValue().metadata().contains("sourceStepId=" + SOURCE_ID));
        assertTrue(event.getValue().metadata().contains("targetStepId=" + TARGET_ID));
    }

    @Test
    // Ends acknowledgement ownership before making a paused next step due.
    void escalatesAnAcknowledgedRunAndCancelsItsResolutionWait() {
        escalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        escalation.setIssueSolvedBy("first@example.com");
        escalation.setAcknowledgedStepId(SOURCE_ID);
        escalation.setAcknowledgedAt(FIXED_NOW.minus(Duration.ofMinutes(2)));
        escalation.setResolutionDeadline(FIXED_NOW.plus(Duration.ofMinutes(8)));
        target.setStatus(FlowExecutionStepStatus.PAUSED);
        when(stateRepository.findByIdForUpdate(SOURCE_ID)).thenReturn(Optional.of(source));
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID, List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED))).thenReturn(target);

        service.escalateNowAsTeamMember(
                ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID));

        assertEquals(EscalationStatus.OPEN, escalation.getStatus());
        assertNull(escalation.getIssueSolvedBy());
        assertNull(escalation.getAcknowledgedAt());
        assertNull(escalation.getAcknowledgedStepId());
        assertNull(escalation.getResolutionDeadline());
        verify(timeoutService).cancelResolutionTimeout(ESCALATION_ID);
        verify(stepSchedulingService).scheduleStepImmediately(target, FIXED_NOW);
    }

    @Test
    // Rejects a stale target or expired response window without scheduling work.
    void rejectsStaleOrExpiredActionsWithoutChangingState() {
        source.setDueAt(FIXED_NOW.minusSeconds(1));
        when(stateRepository.findTopByProcessIdAndStatusOrderByPositionDesc(
                ESCALATION_ID, FlowExecutionStepStatus.SENT)).thenReturn(source);
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID, List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED))).thenReturn(target);

        EscalationException error = assertThrows(
                EscalationException.class,
                () -> service.escalateNowAsTeamMember(
                        ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID)));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(stepSchedulingService, never()).scheduleStepImmediately(any(), any());
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    // Returns the saved result when the same team action is confirmed again.
    void repeatedTeamConfirmationDoesNotScheduleAnotherStep() {
        AuditEventEntity priorAction = org.mockito.Mockito.mock(AuditEventEntity.class);
        when(priorAction.getAction()).thenReturn(AuditAction.ESCALATED_NOW.name());
        when(priorAction.getMetadata()).thenReturn(
                "sourceStepId=" + SOURCE_ID
                        + ";targetStepId=" + TARGET_ID
                        + ";source=TEAM_MEMBER;resolutionWaitEnded=false;tokenHash=none");
        when(auditEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                "ESCALATION", ESCALATION_ID)).thenReturn(List.of(priorAction));

        var result = service.escalateNowAsTeamMember(
                ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID));

        assertTrue(result.alreadyEscalated());
        verify(stepSchedulingService, never()).scheduleStepImmediately(any(), any());
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    // Allows only an explicit Escalate now recipient token to advance the run.
    void recipientTokenCanPreviewAndConfirmOnlyItsBoundTarget() {
        EscalationAcknowledgementToken token = new EscalationAcknowledgementToken();
        token.setEscalationId(ESCALATION_ID);
        token.setExecutionStepId(SOURCE_ID);
        token.setExpectedTargetStepId(TARGET_ID);
        token.setRecipientEmail("first@example.com");
        token.setCapability(EscalationActionCapability.ESCALATE_NOW);
        token.setTokenHash(hash(RAW_TOKEN));
        token.setExpiresAt(FIXED_NOW.plus(Duration.ofHours(1)));
        when(tokenRepository.findByTokenHash(hash(RAW_TOKEN))).thenReturn(Optional.of(token));
        when(stateRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(stateRepository.findTopByProcessIdAndStatusOrderByPositionDesc(
                ESCALATION_ID, FlowExecutionStepStatus.SENT)).thenReturn(source);
        when(stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                ESCALATION_ID, List.of(FlowExecutionStepStatus.PAUSED,
                        FlowExecutionStepStatus.PENDING,
                        FlowExecutionStepStatus.SCHEDULED))).thenReturn(target);

        var preview = service.previewAsRecipient(RAW_TOKEN);
        assertTrue(preview.actionAvailable());
        service.escalateNowAsRecipient(RAW_TOKEN);

        verify(stepSchedulingService).scheduleStepImmediately(target, FIXED_NOW);
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    // Prevents terminal runs and actions without a next step from changing state.
    void rejectsTerminalRunsAndMissingNextSteps() {
        escalation.setStatus(EscalationStatus.COMPLETED);
        var preview = service.previewAsTeamMember(
                ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID));
        assertFalse(preview.actionAvailable());

        escalation.setStatus(EscalationStatus.OPEN);
        when(stateRepository.findAllByProcessIdOrderByPositionAsc(ESCALATION_ID)).thenReturn(List.of(source));
        preview = service.previewAsTeamMember(
                ESCALATION_ID, new EscalationManualActionRequest(SOURCE_ID, TARGET_ID));
        assertFalse(preview.actionAvailable());
        verify(stepSchedulingService, never()).scheduleStepImmediately(any(), any());
    }

    // Creates one durable execution step fixture.
    private FlowExecutionState step(UUID id, FlowExecutionStepStatus status, int position, String email) {
        FlowExecutionState state = new FlowExecutionState();
        state.setId(id);
        state.setProcessId(ESCALATION_ID);
        state.setStatus(status);
        state.setPosition(java.math.BigInteger.valueOf(position));
        state.setUserEmail(email);
        return state;
    }

    // Hashes the test token exactly as the service hashes recipient tokens.
    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
