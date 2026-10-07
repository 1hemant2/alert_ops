package com.alertops.audit.model;

/** Actions recorded in the audit history. */
public enum AuditAction {
    CREATED,
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    START_FAILED,
    NOTIFICATION_SENT,
    NOTIFICATION_FAILED,
    NOTIFICATION_RETRY_SCHEDULED,
    ACKNOWLEDGED,
    ESCALATED_NOW,
    RESOLVED,
    ACKNOWLEDGEMENT_EXPIRED,
    RESOLUTION_EXPIRED,
    COMPLETED
}
