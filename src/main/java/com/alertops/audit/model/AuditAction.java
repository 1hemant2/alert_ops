package com.alertops.audit.model;

/** Actions recorded in the audit history. */
public enum AuditAction {
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    START_FAILED,
    ACKNOWLEDGED,
    RESOLVED,
    ACKNOWLEDGEMENT_EXPIRED,
    RESOLUTION_EXPIRED
}
