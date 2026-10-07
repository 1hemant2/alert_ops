package com.alertops.flow_execution_engine.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
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
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;

@Service
public class EscalationResolutionService {
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final EscalationRepository escalationRepository;
    private final FlowExecutionStateRepository flowExecutionStateRepository;
    private final EscalationAcknowledgementTokenRepository tokenRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final StepTimerRegistry stepTimerRegistry;
    private final Clock clock;

    public EscalationResolutionService(
            EscalationRepository escalationRepository,
            FlowExecutionStateRepository flowExecutionStateRepository,
            EscalationAcknowledgementTokenRepository tokenRepository,
            AuditEventRepository auditEventRepository,
            AuditService auditService,
            StepTimerRegistry stepTimerRegistry,
            Clock clock) {
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.flowExecutionStateRepository = Objects.requireNonNull(
                flowExecutionStateRepository, "flowExecutionStateRepository");
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
        this.auditEventRepository = Objects.requireNonNull(auditEventRepository, "auditEventRepository");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.stepTimerRegistry = Objects.requireNonNull(stepTimerRegistry, "stepTimerRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public EscalationResolutionResponse resolveAsTeamMember(UUID escalationId) {
        AuthContext authContext = requireTeamContext();
        Escalation escalation = findLockedEscalation(escalationId);
        if (!Objects.equals(escalation.getTeamId(), authContext.getTeamId())) {
            throw EscalationException.notFound();
        }
        return resolveLocked(
                escalation,
                authContext.getUserId(),
                requireActorEmail(authContext),
                null,
                null,
                ResolutionSource.TEAM_MEMBER);
    }

    @Transactional
    public EscalationResolutionResponse resolveAsRecipient(String rawToken) {
        EscalationAcknowledgementToken token = findToken(rawToken);
        Escalation escalation = findLockedEscalation(token.getEscalationId());
        validateRecipientTokenShape(token);

        if (escalation.getStatus() == EscalationStatus.RESOLVED) {
            validateResolvedRecipientReplay(token, escalation);
            return response(escalation, true);
        }

        validateRecipientToken(token, escalation);
        return resolveLocked(
                escalation,
                null,
                token.getRecipientEmail(),
                token.getExecutionStepId(),
                token.getTokenHash(),
                ResolutionSource.RECIPIENT_TOKEN);
    }

    private EscalationResolutionResponse resolveLocked(
            Escalation escalation,
            UUID actorId,
            String actorEmail,
            UUID expectedAcknowledgementStepId,
            String expectedTokenHash,
            ResolutionSource source) {
        if (escalation.getStatus() == EscalationStatus.RESOLVED) {
            if (sameEmail(actorEmail, escalation.getResolvedBy())) {
                return response(escalation, true);
            }
            throw EscalationException.transitionConflict("This escalation was resolved by another actor.");
        }
        if (escalation.getStatus() != EscalationStatus.ACKNOWLEDGED) {
            throw EscalationException.transitionConflict(
                    "Only an acknowledged escalation can be resolved.");
        }
        if (source == ResolutionSource.RECIPIENT_TOKEN
                && !Objects.equals(expectedAcknowledgementStepId, escalation.getAcknowledgedStepId())) {
            throw EscalationException.transitionConflict(
                    "This acknowledgement is no longer the current resolution owner.");
        }

        Instant now = clock.instant();
        Instant resolutionDeadline = escalation.getResolutionDeadline();
        if (resolutionDeadline == null || !now.isBefore(resolutionDeadline)) {
            throw EscalationException.transitionConflict("The resolution deadline has passed.");
        }
        UUID acknowledgedStepId = escalation.getAcknowledgedStepId();

        List<FlowExecutionState> unsentSteps = flowExecutionStateRepository
                .findUnsentStepsForUpdate(escalation.getId());
        List<UUID> timerStepIds = unsentSteps.stream()
                .filter(step -> step.getStatus() == FlowExecutionStepStatus.SCHEDULED
                        || step.getStatus() == FlowExecutionStepStatus.PAUSED)
                .map(FlowExecutionState::getId)
                .filter(Objects::nonNull)
                .toList();
        for (FlowExecutionState step : unsentSteps) {
            step.setStatus(FlowExecutionStepStatus.SKIPPED);
            step.setPublicationPending(false);
            step.setDueAt(null);
        }
        if (!unsentSteps.isEmpty()) {
            flowExecutionStateRepository.saveAll(unsentSteps);
        }

        escalation.setStatus(EscalationStatus.RESOLVED);
        escalation.setResolutionType(null);
        escalation.setResolvedBy(actorEmail.trim());
        escalation.setResolvedAt(now);
        escalation.setIssueSolvedBy(null);
        escalation.setAcknowledgedAt(null);
        escalation.setAcknowledgedStepId(null);
        escalation.setResolutionDeadline(null);
        escalationRepository.save(escalation);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                escalation.getId(),
                AuditAction.RESOLVED,
                EscalationStatus.ACKNOWLEDGED.name(),
                EscalationStatus.RESOLVED.name(),
                actorId,
                actorEmail,
                now,
                null,
                "acknowledgedStepId=" + acknowledgedStepId + ";source=" + source.name()
                        + ";tokenHash=" + (expectedTokenHash == null ? "none" : expectedTokenHash)));
        cancelTimersAfterCommit(timerStepIds);
        return response(escalation, false);
    }

    private void validateRecipientToken(
            EscalationAcknowledgementToken token,
            Escalation escalation) {
        if (token.getExecutionStepId() == null
                || !Objects.equals(token.getExecutionStepId(), escalation.getAcknowledgedStepId())
                || !sameEmail(token.getRecipientEmail(), escalation.getIssueSolvedBy())) {
            throw EscalationException.transitionConflict(
                    "This acknowledgement is no longer the current resolution owner.");
        }
        FlowExecutionState step = flowExecutionStateRepository.findById(token.getExecutionStepId())
                .orElseThrow(() -> invalidToken());
        if (!Objects.equals(step.getProcessId(), escalation.getId())
                || step.getStatus() != FlowExecutionStepStatus.SENT
                || !sameEmail(step.getUserEmail(), token.getRecipientEmail())) {
            throw EscalationException.transitionConflict(
                    "This acknowledgement is no longer the current resolution owner.");
        }
    }

    private void validateResolvedRecipientReplay(
            EscalationAcknowledgementToken token,
            Escalation escalation) {
        if (!sameEmail(token.getRecipientEmail(), escalation.getResolvedBy())) {
            throw EscalationException.transitionConflict("This escalation was resolved by another actor.");
        }
        String expectedMetadata = "acknowledgedStepId=" + token.getExecutionStepId()
                + ";source=" + ResolutionSource.RECIPIENT_TOKEN.name()
                + ";tokenHash=" + token.getTokenHash();
        List<AuditEventEntity> resolutionEvents = auditEventRepository
                .findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                        AuditEntityType.ESCALATION.name(), escalation.getId())
                .stream()
                .filter(event -> AuditAction.RESOLVED.name().equals(event.getAction()))
                .toList();
        AuditEventEntity resolutionEvent = resolutionEvents.isEmpty()
                ? null
                : resolutionEvents.get(resolutionEvents.size() - 1);
        if (resolutionEvent == null
                || !sameEmail(resolutionEvent.getUserEmail(), token.getRecipientEmail())
                || !Objects.equals(expectedMetadata, resolutionEvent.getMetadata())) {
            throw EscalationException.transitionConflict(
                    "This acknowledgement is no longer the current resolution owner.");
        }
    }

    private void validateRecipientTokenShape(EscalationAcknowledgementToken token) {
        if (isBlank(token.getRecipientEmail()) || token.getExpiresAt() == null
                || isBlank(token.getTokenHash())) {
            throw invalidToken();
        }
    }

    private EscalationAcknowledgementToken findToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw invalidToken();
        }
        return tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(this::invalidToken);
    }

    private Escalation findLockedEscalation(UUID escalationId) {
        if (escalationId == null) {
            throw EscalationException.invalidRequest("An escalationId is required.");
        }
        return escalationRepository.findByIdForUpdate(escalationId)
                .orElseThrow(EscalationException::notFound);
    }

    private AuthContext requireTeamContext() {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null || authContext.getUserId() == null) {
            throw EscalationException.unauthorized();
        }
        if (authContext.getTeamId() == null) {
            throw EscalationException.forbidden("Select a team before resolving an escalation.");
        }
        return authContext;
    }

    private String requireActorEmail(AuthContext authContext) {
        String email = authContext.getEmail();
        if (email == null || email.isBlank()) {
            throw EscalationException.unauthorized();
        }
        return email.trim();
    }

    private void cancelTimersAfterCommit(List<UUID> stepIds) {
        if (stepIds.isEmpty()) {
            return;
        }
        Runnable cancellation = () -> stepIds.forEach(stepTimerRegistry::cancel);
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

    private EscalationResolutionResponse response(
            Escalation escalation,
            boolean alreadyResolved) {
        return new EscalationResolutionResponse(
                escalation.getName(),
                escalation.getStatus() == null ? null : escalation.getStatus().name(),
                escalation.getResolvedBy(),
                escalation.getResolvedAt(),
                alreadyResolved);
    }

    private boolean sameEmail(String first, String second) {
        return normalizeEmail(first).equals(normalizeEmail(second));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This resolution link is invalid.");
    }

    private enum ResolutionSource {
        TEAM_MEMBER,
        RECIPIENT_TOKEN
    }
}
