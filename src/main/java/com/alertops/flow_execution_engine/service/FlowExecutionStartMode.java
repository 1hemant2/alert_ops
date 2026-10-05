package com.alertops.flow_execution_engine.service;

/** Identifies which lifecycle claim a flow start must perform. */
public enum FlowExecutionStartMode {
    IDLE,
    SCHEDULED_DUE,
    SCHEDULED_EARLY
}
