package com.alertops.flow_execution_engine.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.dto.EscalationAcknowledgementResponse;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationAcknowledgementToken;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationAcknowledgementTokenRepository;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.StepTimerRegistry;
import com.alertops.messaging.EscalationTimeoutService;

@Service
public class EscalationAcknowledgementService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final EscalationAcknowledgementTokenRepository tokenRepository;
    private final EscalationRepository escalationRepository;
    private final FlowExecutionStateRepository flowExecutionStateRepository;
    private final AuditService auditService;
    private final StepTimerRegistry stepTimerRegistry;
    private final EscalationTimeoutService timeoutService;
    private final Clock clock;
    private final Duration tokenLifetime;
    private final String uiBaseUrl;

    // Creates the service that validates and records acknowledgement actions.
    public EscalationAcknowledgementService(
            EscalationAcknowledgementTokenRepository tokenRepository,
            EscalationRepository escalationRepository,
            FlowExecutionStateRepository flowExecutionStateRepository,
            AuditService auditService,
            StepTimerRegistry stepTimerRegistry,
            EscalationTimeoutService timeoutService,
            Clock clock,
            @Value("${alertops.escalation.acknowledgement-ttl:72h}") Duration tokenLifetime,
            @Value("${alertops.ui.base-url:http://localhost:5173}") String uiBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.escalationRepository = escalationRepository;
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.auditService = auditService;
        this.stepTimerRegistry = stepTimerRegistry;
        this.timeoutService = timeoutService;
        this.clock = clock;
        this.tokenLifetime = tokenLifetime;
        this.uiBaseUrl = uiBaseUrl == null ? "" : uiBaseUrl.replaceAll("/+$", "");
        if (tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException("Acknowledgement link lifetime must be positive");
        }
    }

    @Transactional
    public String createAcknowledgementUrl(Escalation escalation, FlowExecutionState executionStep) {
        if (escalation == null || escalation.getId() == null || executionStep == null
                || executionStep.getId() == null
                || !escalation.getId().equals(executionStep.getProcessId())
                || isBlank(executionStep.getUserEmail())) {
            throw new IllegalArgumentException("An escalation and exact execution step are required to create an acknowledgement link");
        }

        String rawToken = newRawToken();
        Instant now = clock.instant();
        EscalationAcknowledgementToken token = new EscalationAcknowledgementToken();
        token.setEscalationId(escalation.getId());
        token.setExecutionStepId(executionStep.getId());
        token.setRecipientEmail(executionStep.getUserEmail().trim());
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(tokenLifetime));
        tokenRepository.save(token);

        return uiBaseUrl + "/acknowledge?token=" + rawToken;
    }

    @Transactional(readOnly = true)
    public EscalationAcknowledgementResponse preview(String rawToken) {
        EscalationAcknowledgementToken token = findToken(rawToken);
        Escalation escalation = escalationRepository.findById(token.getEscalationId())
                .orElseThrow(() -> invalidToken());
        validateTokenStep(token, escalation);
        boolean alreadyAcknowledged = isAcknowledgedBy(escalation, token);
        validateTokenAndRun(token, escalation, alreadyAcknowledged);
        return response(token, escalation, alreadyAcknowledged);
    }

    @Transactional
    // Records an acknowledgement after validating token, ownership, and timeout.
    public EscalationAcknowledgementResponse acknowledge(String rawToken) {
        EscalationAcknowledgementToken token = findToken(rawToken);
        Escalation escalation = escalationRepository.findByIdForUpdate(token.getEscalationId())
                .orElseThrow(() -> invalidToken());
        FlowExecutionState executionStep = validateTokenStep(token, escalation);
        validateCurrentAcknowledgementStep(token, executionStep, escalation);
        boolean alreadyAcknowledged = isAcknowledgedBy(escalation, token);
        validateTokenAndRun(token, escalation, alreadyAcknowledged);

        if (!alreadyAcknowledged) {
            validateAcknowledgementTimeout(executionStep);
            Instant acknowledgedAt = clock.instant();
            if (executionStep.isResolutionTimeoutEnabled()) {
                acknowledgeWithResolutionTimeout(escalation, token, executionStep, acknowledgedAt);
            } else {
                completeWithoutResolutionTimeout(escalation, token, acknowledgedAt);
            }
        }

        return response(token, escalation, true);
    }

    private EscalationAcknowledgementToken findToken(String rawToken) {
        if (isBlank(rawToken) || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw invalidToken();
        }
        return tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> invalidToken());
    }

    private void validateTokenAndRun(
            EscalationAcknowledgementToken token,
            Escalation escalation,
            boolean alreadyAcknowledged) {
        if (alreadyAcknowledged) {
            return;
        }
        if (!token.getExpiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This acknowledgement link has expired.");
        }
        if (escalation.getStatus() == EscalationStatus.ACKNOWLEDGED
                || escalation.getResolutionType() == EscalationResolutionType.ACKNOWLEDGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This escalation was acknowledged by another recipient.");
        }
        if (escalation.getStatus() != EscalationStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This escalation is no longer active.");
        }
    }

    private boolean isAcknowledgedBy(Escalation escalation, EscalationAcknowledgementToken token) {
        if (!normalizeEmail(token.getRecipientEmail()).equals(normalizeEmail(escalation.getIssueSolvedBy()))) {
            return false;
        }
        if (escalation.getStatus() == EscalationStatus.ACKNOWLEDGED) {
            return Objects.equals(escalation.getAcknowledgedStepId(), token.getExecutionStepId());
        }
        return escalation.getStatus() == EscalationStatus.COMPLETED
                && escalation.getResolutionType() == EscalationResolutionType.ACKNOWLEDGED;
    }

    private FlowExecutionState validateTokenStep(
            EscalationAcknowledgementToken token,
            Escalation escalation) {
        if (token.getExecutionStepId() == null) {
            throw invalidToken();
        }
        FlowExecutionState executionStep = flowExecutionStateRepository.findById(token.getExecutionStepId())
                .orElseThrow(() -> invalidToken());
        if (!Objects.equals(escalation.getId(), token.getEscalationId())
                || !Objects.equals(token.getEscalationId(), executionStep.getProcessId())
                || !normalizeEmail(token.getRecipientEmail()).equals(normalizeEmail(executionStep.getUserEmail()))) {
            throw invalidToken();
        }
        return executionStep;
    }

    private void validateCurrentAcknowledgementStep(
            EscalationAcknowledgementToken token,
            FlowExecutionState executionStep,
            Escalation escalation) {
        if (executionStep.getStatus() != FlowExecutionStepStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This acknowledgement is no longer available for the execution step.");
        }
        FlowExecutionState currentSentStep = flowExecutionStateRepository
                .findTopByProcessIdAndStatusOrderByPositionDesc(
                        escalation.getId(), FlowExecutionStepStatus.SENT);
        if (currentSentStep == null || !Objects.equals(currentSentStep.getId(), token.getExecutionStepId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This acknowledgement is no longer available for the execution step.");
        }
    }

    // Completes acknowledgement when no resolution timeout is configured.
    private void completeWithoutResolutionTimeout(
            Escalation escalation,
            EscalationAcknowledgementToken token,
            Instant acknowledgedAt) {
        escalation.setStatus(EscalationStatus.COMPLETED);
        escalation.setResolutionType(EscalationResolutionType.ACKNOWLEDGED);
        escalation.setIssueSolvedBy(token.getRecipientEmail());
        escalation.setAcknowledgedAt(acknowledgedAt);
        escalation.setAcknowledgedStepId(null);
        escalation.setResolutionDeadline(null);
        flowExecutionStateRepository.markUnsentStepsSkipped(escalation.getId());
        escalationRepository.save(escalation);
        recordAcknowledgementAudit(escalation, token, acknowledgedAt, EscalationStatus.COMPLETED, null);
        timeoutService.cancelAcknowledgementTimeout(escalation.getId());
    }

    // Pauses the next step and starts the saved resolution timeout.
    private void acknowledgeWithResolutionTimeout(
            Escalation escalation,
            EscalationAcknowledgementToken token,
            FlowExecutionState executionStep,
            Instant acknowledgedAt) {
        Duration resolutionTimeout = executionStep.getResolutionTimeout();
        if (resolutionTimeout == null || resolutionTimeout.isZero() || resolutionTimeout.isNegative()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This execution step has no valid resolution timeout.");
        }

        FlowExecutionState nextStep = flowExecutionStateRepository
                .findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                        escalation.getId(),
                        List.of(FlowExecutionStepStatus.PENDING, FlowExecutionStepStatus.SCHEDULED));
        UUID pausedStepId = null;
        if (nextStep != null) {
            nextStep.setStatus(FlowExecutionStepStatus.PAUSED);
            nextStep.setPublicationPending(false);
            FlowExecutionState savedNextStep = Objects.requireNonNull(
                    flowExecutionStateRepository.save(nextStep), "Paused execution step is required");
            pausedStepId = savedNextStep.getId();
        }

        Instant resolutionTimeoutAt = acknowledgedAt.plus(resolutionTimeout);
        escalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        escalation.setResolutionType(null);
        escalation.setIssueSolvedBy(token.getRecipientEmail());
        escalation.setAcknowledgedAt(acknowledgedAt);
        escalation.setAcknowledgedStepId(executionStep.getId());
        escalation.setResolutionDeadline(resolutionTimeoutAt);
        escalationRepository.save(escalation);
        recordAcknowledgementAudit(
                escalation, token, acknowledgedAt, EscalationStatus.ACKNOWLEDGED, resolutionTimeoutAt);
        timeoutService.cancelAcknowledgementTimeout(escalation.getId());
        timeoutService.scheduleResolutionTimeout(
                escalation.getId(), executionStep.getId(), resolutionTimeoutAt);

        if (pausedStepId != null) {
            cancelTimerAfterCommit(pausedStepId);
        }
    }

    private void recordAcknowledgementAudit(
            Escalation escalation,
            EscalationAcknowledgementToken token,
            Instant acknowledgedAt,
            EscalationStatus newStatus,
            Instant resolutionTimeoutAt) {
        String metadata = "executionStepId=" + token.getExecutionStepId()
                + ";resolutionTimeoutAt=" + (resolutionTimeoutAt == null ? "none" : resolutionTimeoutAt);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                escalation.getId(),
                AuditAction.ACKNOWLEDGED,
                EscalationStatus.OPEN.name(),
                newStatus.name(),
                null,
                token.getRecipientEmail(),
                acknowledgedAt,
                null,
                metadata));
    }

    private void cancelTimerAfterCommit(UUID stepId) {
        Runnable cancellation = () -> stepTimerRegistry.cancel(stepId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cancellation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cancellation.run();
            }
        });
    }

    // Rejects acknowledgement after the shared step timeout has passed.
    private void validateAcknowledgementTimeout(FlowExecutionState executionStep) {
        Instant acknowledgementTimeoutAt = executionStep.getDueAt();
        if (acknowledgementTimeoutAt == null || !clock.instant().isBefore(acknowledgementTimeoutAt)) {
            throw new ResponseStatusException(HttpStatus.GONE, "The acknowledgement window has expired.");
        }
    }

    private EscalationAcknowledgementResponse response(
            EscalationAcknowledgementToken token,
            Escalation escalation,
            boolean alreadyAcknowledged) {
        return new EscalationAcknowledgementResponse(
                escalation.getName(),
                token.getRecipientEmail(),
                escalation.getStatus() == null ? null : escalation.getStatus().name(),
                token.getExpiresAt(),
                escalation.getAcknowledgedAt(),
                escalation.getIssueSolvedBy(),
                alreadyAcknowledged);
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This acknowledgement link is invalid.");
    }
}
