package com.alertops.flow_execution_engine.model;

/** Lifecycle states supported by an escalation run. */
public enum EscalationStatus {
    IDLE,
    SCHEDULED,
    OPEN,
    COMPLETED,
    CANCELLED,
    START_FAILED
}
