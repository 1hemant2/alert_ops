package com.alertops.audit.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.model.AuditEventEntity;
import com.alertops.audit.repository.AuditEventRepository;

/**
 * Persists append-only events for any entity without changing domain models.
 * Lifecycle services own transition decisions; this service owns audit storage.
 * A REQUIRED transaction joins the caller's transition transaction when one exists.
 */
@Service
public class AuditService {
    private static final String DEFAULT_FAILURE_REASON = "SCHEDULED_START_FAILED";
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_ENTITY_TYPE_LENGTH = 64;
    private static final int MAX_ACTION_LENGTH = 128;

    private final AuditEventRepository eventRepository;

    public AuditService(AuditEventRepository eventRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
    }

    @Transactional
    public void record(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Audit event is required");
        }
        if (event.entityType() == null || event.action() == null
                || event.entityId() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("Audit event entity, action, id, and time are required");
        }
        validateText(event.entityType().name(), "Audit entity type", MAX_ENTITY_TYPE_LENGTH);
        validateText(event.action().name(), "Audit action", MAX_ACTION_LENGTH);
        String userEmail = normalizeUserEmail(event.userEmail());
        String reason = normalizeReason(event.action(), event.reason());
        eventRepository.save(new AuditEventEntity(new AuditEvent(
                event.entityType(), event.entityId(), event.action(),
                normalizeState(event.previousState()), normalizeState(event.newState()),
                event.userId(), userEmail,
                event.occurredAt(), reason, event.metadata())));
    }

    private void validateText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
    }

    private String normalizeUserEmail(String userEmail) {
        if (userEmail == null) {
            return null;
        }
        String trimmed = userEmail.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeState(String state) {
        if (state == null) {
            return null;
        }
        String trimmed = state.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeReason(AuditAction action, String reason) {
        // Keep the audit reason short and safe for user-facing history. Detailed
        // exception diagnostics should remain in application logs or metadata.
        if (reason == null || reason.isBlank()) {
            return action == AuditAction.START_FAILED ? DEFAULT_FAILURE_REASON : null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= MAX_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_REASON_LENGTH);
    }
}
