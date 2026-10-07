package com.alertops.flow_execution_engine.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditActorType;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.repository.AuditEventRepository;
import com.alertops.flow_execution_engine.dto.EscalationHistoryEventResponse;
import com.alertops.flow_execution_engine.dto.EscalationHistoryPageResponse;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;

@Service
public class EscalationHistoryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SENSITIVE_DETAIL_KEYS = Set.of(
            "token", "tokenhash", "secret", "password", "diagnostic", "stacktrace", "exception");

    private final EscalationRepository escalationRepository;
    private final AuditEventRepository auditEventRepository;

    // Creates the read-only service with the repositories used for access and history.
    public EscalationHistoryService(
            EscalationRepository escalationRepository,
            AuditEventRepository auditEventRepository) {
        this.escalationRepository = escalationRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    // Returns one authorized, stable page of an escalation's saved activity.
    public EscalationHistoryPageResponse getHistory(UUID escalationId, int page, int size) {
        validatePageRequest(escalationId, page, size);
        UUID teamId = requireSelectedTeam();
        if (escalationRepository.findByIdAndTeamId(escalationId, teamId) == null) {
            throw EscalationException.notFound();
        }

        Page<AuditEventEntity> historyPage = auditEventRepository.findAllByEntityTypeAndEntityId(
                AuditEntityType.ESCALATION.name(),
                escalationId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.asc("occurredAt"), Sort.Order.asc("id"))));

        return new EscalationHistoryPageResponse(
                historyPage.getContent().stream().map(this::toResponse).toList(),
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.isFirst(),
                historyPage.isLast());
    }

    // Requires an authenticated request with a selected team context.
    private UUID requireSelectedTeam() {
        AuthContext context = AuthContextHolder.get();
        if (context == null) {
            throw EscalationException.unauthorized();
        }
        if (context.getTeamId() == null) {
            throw EscalationException.forbidden("Select a team before viewing escalation history.");
        }
        return context.getTeamId();
    }

    // Rejects invalid or unreasonably large history pages at the API boundary.
    private void validatePageRequest(UUID escalationId, int page, int size) {
        if (escalationId == null) {
            throw EscalationException.invalidRequest("An escalationId is required.");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw EscalationException.invalidRequest("History page must be nonnegative and contain 1 to 100 events.");
        }
    }

    // Maps a stored audit row to the safe fields exposed by the history API.
    private EscalationHistoryEventResponse toResponse(AuditEventEntity event) {
        String actorEmail = blankToNull(event.getUserEmail());
        AuditActorType actorType = event.getUserId() == null && actorEmail == null
                ? AuditActorType.SYSTEM
                : AuditActorType.USER;
        return new EscalationHistoryEventResponse(
                event.getId(),
                parseAction(event.getAction()),
                event.getPreviousState(),
                event.getNewState(),
                actorType,
                event.getUserId(),
                actorEmail,
                event.getOccurredAt(),
                safeReason(event.getReason()),
                safeDetails(event.getMetadata()));
    }

    // Parses the persisted action into the application's finite audit vocabulary.
    private AuditAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalStateException("Audit action is missing");
        }
        try {
            return AuditAction.valueOf(action.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Audit action is unsupported", exception);
        }
    }

    // Removes blank or sensitive reason text before it reaches a client.
    private String safeReason(String reason) {
        String normalized = blankToNull(reason);
        if (normalized == null || containsSensitiveText(normalized)) {
            return null;
        }
        return normalized;
    }

    // Converts safe key-value metadata into structured details and removes secrets.
    private Map<String, String> safeDetails(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        Map<String, String> details = new LinkedHashMap<>();
        for (String entry : metadata.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (key.isEmpty() || value.isEmpty() || isSensitiveKey(key)) {
                continue;
            }
            details.put(key, value);
        }
        return details.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(details);
    }

    // Identifies metadata names that must never be returned as activity details.
    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_DETAIL_KEYS.stream().anyMatch(normalized::contains);
    }

    // Detects sensitive content in free-form audit reasons.
    private boolean containsSensitiveText(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("diagnostic")
                || normalized.contains("stacktrace")
                || normalized.contains("exception");
    }

    // Treats blank persisted text as absent API data.
    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
