package com.alertops.flow_execution_engine.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.repository.AuditEventRepository;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.dto.EscalationManualActionRequest;
import com.alertops.flow_execution_engine.dto.EscalationManualActionResponse;
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

/** Advances the next notification immediately when an authorized actor escalates now. */
@Service
public class EscalationManualActionService {
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final List<FlowExecutionStepStatus> NEXT_STEP_STATUSES = List.of(
            FlowExecutionStepStatus.PAUSED,
            FlowExecutionStepStatus.PENDING,
            FlowExecutionStepStatus.SCHEDULED);

    private final EscalationRepository escalationRepository;
    private final FlowExecutionStateRepository stateRepository;
    private final EscalationAcknowledgementTokenRepository tokenRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final StepSchedulingService stepSchedulingService;
    private final EscalationTimeoutService timeoutService;
    private final Clock clock;

    // Creates the service that validates and applies Escalate now actions.
    public EscalationManualActionService(
            EscalationRepository escalationRepository,
            FlowExecutionStateRepository stateRepository,
            EscalationAcknowledgementTokenRepository tokenRepository,
            AuditEventRepository auditEventRepository,
            AuditService auditService,
            StepSchedulingService stepSchedulingService,
            EscalationTimeoutService timeoutService,
            Clock clock) {
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
        this.auditEventRepository = Objects.requireNonNull(auditEventRepository, "auditEventRepository");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.stepSchedulingService = Objects.requireNonNull(stepSchedulingService, "stepSchedulingService");
        this.timeoutService = Objects.requireNonNull(timeoutService, "timeoutService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // Previews an authenticated team member's expected Escalate now action.
    @Transactional(readOnly = true)
    public EscalationManualActionResponse previewAsTeamMember(
            UUID escalationId,
            EscalationManualActionRequest request) {
        AuthContext authContext = requireTeamContext();
        validateRequest(request);
        Escalation escalation = findEscalation(escalationId);
        if (!Objects.equals(escalation.getTeamId(), authContext.getTeamId())) {
            throw EscalationException.notFound();
        }
        return previewAction(escalation, request.expectedSourceStepId(), request.expectedTargetStepId(), null);
    }

    // Applies an authenticated team member's expected Escalate now action.
    @Transactional
    public EscalationManualActionResponse escalateNowAsTeamMember(
            UUID escalationId,
            EscalationManualActionRequest request) {
        AuthContext authContext = requireTeamContext();
        validateRequest(request);
        Escalation escalation = findLockedEscalation(escalationId);
        if (!Objects.equals(escalation.getTeamId(), authContext.getTeamId())) {
            throw EscalationException.notFound();
        }
        return applyEscalateNowTransition(
                escalation,
                request.expectedSourceStepId(),
                request.expectedTargetStepId(),
                authContext.getUserId(),
                requireActorEmail(authContext),
                null,
                ActionSource.TEAM_MEMBER);
    }

    // Previews a recipient token without changing durable escalation state.
    @Transactional(readOnly = true)
    public EscalationManualActionResponse previewAsRecipient(String rawToken) {
        EscalationAcknowledgementToken token = findEscalateNowToken(rawToken);
        Escalation escalation = findEscalation(token.getEscalationId());
        validateRecipientTokenScope(token, escalation);
        return previewAction(
                escalation,
                token.getExecutionStepId(),
                token.getExpectedTargetStepId(),
                token.getRecipientEmail());
    }

    // Applies a recipient token's expected Escalate now action.
    @Transactional
    public EscalationManualActionResponse escalateNowAsRecipient(String rawToken) {
        EscalationAcknowledgementToken token = findEscalateNowToken(rawToken);
        Escalation escalation = findLockedEscalation(token.getEscalationId());
        validateRecipientTokenScope(token, escalation);
        return applyEscalateNowTransition(
                escalation,
                token.getExecutionStepId(),
                token.getExpectedTargetStepId(),
                null,
                token.getRecipientEmail(),
                token.getTokenHash(),
                ActionSource.RECIPIENT_TOKEN);
    }

    // Builds a safe preview while keeping response deadlines separate from token lifetime.
    private EscalationManualActionResponse previewAction(
            Escalation escalation,
            UUID expectedSourceStepId,
            UUID expectedTargetStepId,
            String recipientEmail) {
        List<FlowExecutionState> steps = loadSteps(escalation.getId());
        FlowExecutionState source = findStep(steps, expectedSourceStepId);
        FlowExecutionState target = findStep(steps, expectedTargetStepId);
        AuditEventEntity acceptedAction = findAcceptedEscalateNowAction(
                escalation.getId(), expectedSourceStepId, expectedTargetStepId);
        if (acceptedAction != null) {
            return buildActionResponse(
                    escalation,
                    source,
                    target,
                    actionDeadline(escalation, source),
                    false,
                    true,
                    "Escalate now was already accepted for these steps.");
        }

        ActionCheck check = checkEscalateNowEligibility(
                escalation, steps, expectedSourceStepId, expectedTargetStepId, recipientEmail);
        return buildActionResponse(
                escalation,
                source,
                target,
                check.deadline(),
                check.available(),
                false,
                check.reason());
    }

    // Applies one Escalate now transition after the escalation row has been locked.
    private EscalationManualActionResponse applyEscalateNowTransition(
            Escalation escalation,
            UUID expectedSourceStepId,
            UUID expectedTargetStepId,
            UUID actorId,
            String actorEmail,
            String tokenHash,
            ActionSource actionSource) {
        AuditEventEntity acceptedAction = findAcceptedEscalateNowAction(
                escalation.getId(), expectedSourceStepId, expectedTargetStepId);
        if (acceptedAction != null) {
            return buildAlreadyEscalatedResponse(escalation, expectedSourceStepId, expectedTargetStepId);
        }

        if (escalation.getStatus() != EscalationStatus.OPEN
                && escalation.getStatus() != EscalationStatus.ACKNOWLEDGED) {
            throw EscalationException.transitionConflict("Only an active escalation can be escalated now.");
        }

        FlowExecutionState source = loadCurrentSourceForUpdate(escalation);
        FlowExecutionState target = loadNextStepForUpdate(escalation.getId());
        List<FlowExecutionState> lockedStates = new ArrayList<>();
        if (source != null) {
            lockedStates.add(source);
        }
        if (target != null) {
            lockedStates.add(target);
        }
        ActionCheck check = checkEscalateNowEligibility(
                escalation,
                lockedStates,
                expectedSourceStepId,
                expectedTargetStepId,
                actionSource == ActionSource.RECIPIENT_TOKEN ? actorEmail : null);
        if (!check.available()) {
            throw EscalationException.transitionConflict(check.reason());
        }

        Instant now = clock.instant();
        EscalationStatus previousStatus = escalation.getStatus();
        stepSchedulingService.scheduleStepImmediately(target, now);
        boolean endedResolutionWait = previousStatus == EscalationStatus.ACKNOWLEDGED;
        if (endedResolutionWait) {
            clearAcknowledgementOwnership(escalation);
            timeoutService.cancelResolutionTimeout(escalation.getId());
        }
        escalation.setStatus(EscalationStatus.OPEN);
        escalation.setResolutionType(null);
        escalationRepository.save(escalation);
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                escalation.getId(),
                AuditAction.ESCALATED_NOW,
                previousStatus.name(),
                EscalationStatus.OPEN.name(),
                actorId,
                actorEmail,
                now,
                "ESCALATE_NOW",
                "sourceStepId=" + source.getId()
                        + ";targetStepId=" + target.getId()
                        + ";source=" + actionSource.name()
                        + ";resolutionWaitEnded=" + endedResolutionWait
                        + ";tokenHash=" + (tokenHash == null ? "none" : tokenHash)));
        return buildActionResponse(
                escalation,
                source,
                target,
                check.deadline(),
                false,
                true,
                "Escalate now was accepted.");
    }

    // Checks the exact current source, target, actor scope, and relevant deadline.
    private ActionCheck checkEscalateNowEligibility(
            Escalation escalation,
            List<FlowExecutionState> states,
            UUID expectedSourceStepId,
            UUID expectedTargetStepId,
            String recipientEmail) {
        if (expectedSourceStepId == null || expectedTargetStepId == null) {
            return actionUnavailable("The expected source and next step are required.", null);
        }
        if (escalation.getStatus() != EscalationStatus.OPEN
                && escalation.getStatus() != EscalationStatus.ACKNOWLEDGED) {
            return actionUnavailable("This escalation is no longer active.", null);
        }

        FlowExecutionState source = findStep(states, expectedSourceStepId);
        FlowExecutionState target = findStep(states, expectedTargetStepId);
        if (source == null || target == null || source.getStatus() != FlowExecutionStepStatus.SENT) {
            return actionUnavailable("This escalation has moved to another step.", actionDeadline(escalation, source));
        }
        if (!Objects.equals(source.getProcessId(), escalation.getId())
                || !Objects.equals(target.getProcessId(), escalation.getId())) {
            return actionUnavailable("The requested steps do not belong to this escalation.", null);
        }

        FlowExecutionState currentSource = escalation.getStatus() == EscalationStatus.ACKNOWLEDGED
                ? findStep(states, escalation.getAcknowledgedStepId())
                : findLatestSentStep(states);
        FlowExecutionState currentTarget = findNextEligibleStep(states);
        Instant deadline = actionDeadline(escalation, currentSource);
        if (currentSource == null || !Objects.equals(currentSource.getId(), expectedSourceStepId)) {
            return actionUnavailable("This escalation has moved to another step.", deadline);
        }
        if (currentTarget == null || !Objects.equals(currentTarget.getId(), expectedTargetStepId)
                || !isEligibleNextStep(target)) {
            return actionUnavailable("The expected next step is no longer available.", deadline);
        }
        if (recipientEmail != null
                && escalation.getStatus() == EscalationStatus.ACKNOWLEDGED
                && !matchesEmail(escalation.getIssueSolvedBy(), recipientEmail)) {
            return actionUnavailable("This escalation is owned by another recipient.", deadline);
        }
        if (recipientEmail != null && !matchesEmail(source.getUserEmail(), recipientEmail)) {
            return actionUnavailable("This recipient is not assigned to the current step.", deadline);
        }
        if (deadline == null || !clock.instant().isBefore(deadline)) {
            return actionUnavailable(
                    deadline == null
                            ? "This escalation has no active response window."
                            : "The response window has expired.",
                    deadline);
        }
        return new ActionCheck(true, null, deadline);
    }

    // Loads the current sent source while preserving the run's lock order.
    private FlowExecutionState loadCurrentSourceForUpdate(Escalation escalation) {
        if (escalation.getStatus() == EscalationStatus.ACKNOWLEDGED) {
            UUID acknowledgedStepId = escalation.getAcknowledgedStepId();
            if (acknowledgedStepId == null) {
                return null;
            }
            Optional<FlowExecutionState> result = stateRepository.findByIdForUpdate(acknowledgedStepId);
            return result == null ? null : result.orElse(null);
        }
        return stateRepository.findTopByProcessIdAndStatusOrderByPositionDesc(
                escalation.getId(), FlowExecutionStepStatus.SENT);
    }

    // Locks the next eligible unsent step before changing its due time.
    private FlowExecutionState loadNextStepForUpdate(UUID escalationId) {
        return stateRepository.findFirstByProcessIdAndStatusInOrderByPositionAscIdAsc(
                escalationId, NEXT_STEP_STATUSES);
    }

    // Returns the latest sent step in the saved execution order.
    private FlowExecutionState findLatestSentStep(List<FlowExecutionState> states) {
        FlowExecutionState latest = null;
        for (FlowExecutionState state : states) {
            if (state != null && state.getStatus() == FlowExecutionStepStatus.SENT) {
                latest = state;
            }
        }
        return latest;
    }

    // Returns the first unfinished step in the saved execution order.
    private FlowExecutionState findNextEligibleStep(List<FlowExecutionState> states) {
        for (FlowExecutionState state : states) {
            if (isEligibleNextStep(state)) {
                return state;
            }
        }
        return null;
    }

    // Checks whether a step can receive immediate delivery without skipping it.
    private boolean isEligibleNextStep(FlowExecutionState state) {
        return state != null && NEXT_STEP_STATUSES.contains(state.getStatus());
    }

    // Returns the deadline that controls this actor's current action window.
    private Instant actionDeadline(Escalation escalation, FlowExecutionState source) {
        if (escalation.getStatus() == EscalationStatus.ACKNOWLEDGED) {
            return escalation.getResolutionDeadline();
        }
        return source == null ? null : source.getDueAt();
    }

    // Clears active acknowledgement ownership when manual escalation ends its resolution wait.
    private void clearAcknowledgementOwnership(Escalation escalation) {
        escalation.setIssueSolvedBy(null);
        escalation.setAcknowledgedAt(null);
        escalation.setAcknowledgedStepId(null);
        escalation.setResolutionDeadline(null);
    }

    // Finds the durable escalation without holding a transition lock.
    private Escalation findEscalation(UUID escalationId) {
        if (escalationId == null) {
            throw EscalationException.notFound();
        }
        Optional<Escalation> result = escalationRepository.findById(escalationId);
        if (result == null) {
            throw EscalationException.notFound();
        }
        return result.orElseThrow(EscalationException::notFound);
    }

    // Finds the durable escalation while serializing lifecycle transitions.
    private Escalation findLockedEscalation(UUID escalationId) {
        if (escalationId == null) {
            throw EscalationException.notFound();
        }
        Optional<Escalation> result = escalationRepository.findByIdForUpdate(escalationId);
        if (result == null) {
            throw EscalationException.notFound();
        }
        return result.orElseThrow(EscalationException::notFound);
    }

    // Loads an exact recipient token and requires its manual-action capability.
    private EscalationAcknowledgementToken findEscalateNowToken(String rawToken) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw invalidToken();
        }
        String tokenHash = hash(rawToken);
        Optional<EscalationAcknowledgementToken> tokenResult = tokenRepository.findByTokenHash(tokenHash);
        if (tokenResult == null) {
            throw invalidToken();
        }
        EscalationAcknowledgementToken token = tokenResult.orElseThrow(this::invalidToken);
        if (token.getCapability() != EscalationActionCapability.ESCALATE_NOW) {
            throw invalidToken();
        }
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This Escalate now link has expired.");
        }
        return token;
    }

    // Verifies that a recipient token still belongs to its saved run and source step.
    private void validateRecipientTokenScope(
            EscalationAcknowledgementToken token,
            Escalation escalation) {
        if (token.getExecutionStepId() == null
                || token.getExpectedTargetStepId() == null
                || !Objects.equals(token.getEscalationId(), escalation.getId())
                || token.getRecipientEmail() == null
                || token.getRecipientEmail().isBlank()) {
            throw invalidToken();
        }
        Optional<FlowExecutionState> sourceResult = stateRepository.findById(token.getExecutionStepId());
        FlowExecutionState source = sourceResult == null ? null : sourceResult.orElse(null);
        if (source == null
                || !Objects.equals(source.getProcessId(), escalation.getId())
                || !matchesEmail(source.getUserEmail(), token.getRecipientEmail())) {
            throw invalidToken();
        }
    }

    // Returns a previous identical manual action for idempotent confirmations.
    private AuditEventEntity findAcceptedEscalateNowAction(
            UUID escalationId,
            UUID sourceStepId,
            UUID targetStepId) {
        if (escalationId == null || sourceStepId == null || targetStepId == null) {
            return null;
        }
        String expectedMetadata = "sourceStepId=" + sourceStepId
                + ";targetStepId=" + targetStepId
                + ";source=";
        List<AuditEventEntity> events = auditEventRepository
                .findAllByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                        AuditEntityType.ESCALATION.name(), escalationId);
        if (events == null) {
            return null;
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            AuditEventEntity event = events.get(index);
            if (event != null
                    && AuditAction.ESCALATED_NOW.name().equals(event.getAction())
                    && event.getMetadata() != null
                    && event.getMetadata().startsWith(expectedMetadata)) {
                return event;
            }
        }
        return null;
    }

    // Creates a response after an already accepted manual action.
    private EscalationManualActionResponse buildAlreadyEscalatedResponse(
            Escalation escalation,
            UUID sourceStepId,
            UUID targetStepId) {
        List<FlowExecutionState> steps = loadSteps(escalation.getId());
        FlowExecutionState source = findStep(steps, sourceStepId);
        FlowExecutionState target = findStep(steps, targetStepId);
        return buildActionResponse(
                escalation,
                source,
                target,
                actionDeadline(escalation, source),
                false,
                true,
                "Escalate now was already accepted for these steps.");
    }

    // Builds the public response without exposing token material.
    private EscalationManualActionResponse buildActionResponse(
            Escalation escalation,
            FlowExecutionState source,
            FlowExecutionState target,
            Instant deadline,
            boolean actionAvailable,
            boolean alreadyEscalated,
            String unavailableReason) {
        return new EscalationManualActionResponse(
                escalation.getName(),
                escalation.getStatus() == null ? null : escalation.getStatus().name(),
                source == null ? null : source.getId(),
                source == null ? null : source.getUserEmail(),
                target == null ? null : target.getId(),
                target == null ? null : target.getUserEmail(),
                deadline,
                actionAvailable,
                alreadyEscalated,
                unavailableReason);
    }

    // Builds an unavailable action result with the current deadline.
    private ActionCheck actionUnavailable(String reason, Instant deadline) {
        return new ActionCheck(false, reason, deadline);
    }

    // Returns all saved steps, treating a null repository result as empty.
    private List<FlowExecutionState> loadSteps(UUID escalationId) {
        List<FlowExecutionState> states = stateRepository.findAllByProcessIdOrderByPositionAsc(escalationId);
        return states == null ? List.of() : states;
    }

    // Returns one exact saved step from an already loaded list.
    private FlowExecutionState findStep(List<FlowExecutionState> states, UUID stepId) {
        if (states == null || stepId == null) {
            return null;
        }
        return states.stream()
                .filter(state -> state != null && Objects.equals(state.getId(), stepId))
                .findFirst()
                .orElse(null);
    }

    // Validates a team request before it reaches transition logic.
    private void validateRequest(EscalationManualActionRequest request) {
        if (request == null || request.expectedSourceStepId() == null || request.expectedTargetStepId() == null) {
            throw EscalationException.invalidRequest("Expected source and target steps are required.");
        }
    }

    // Requires a selected authenticated team context.
    private AuthContext requireTeamContext() {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null || authContext.getTeamId() == null) {
            throw EscalationException.unauthorized();
        }
        return authContext;
    }

    // Requires the authenticated actor email used in audit history.
    private String requireActorEmail(AuthContext authContext) {
        if (authContext.getEmail() == null || authContext.getEmail().isBlank()) {
            throw EscalationException.unauthorized();
        }
        return authContext.getEmail().trim();
    }

    // Compares recipient addresses without case-sensitive differences.
    private boolean matchesEmail(String first, String second) {
        return first != null
                && second != null
                && !first.isBlank()
                && !second.isBlank()
                && first.trim().equalsIgnoreCase(second.trim());
    }

    // Hashes a raw token before repository lookup or audit comparison.
    private String hash(String rawToken) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    // Creates the same safe invalid-token response for unknown or foreign links.
    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This Escalate now link is invalid.");
    }

    private record ActionCheck(boolean available, String reason, Instant deadline) {
    }

    private enum ActionSource {
        TEAM_MEMBER,
        RECIPIENT_TOKEN
    }
}
