package com.alertops.flow_execution_engine.model;

/** Persisted lifecycle of one notification step. */
public enum FlowExecutionStepStatus {
    PENDING(false),
    SCHEDULED(false),
    PAUSED(false),
    SENDING(false),
    SENT(true),
    FAILED(true),
    SKIPPED(true);

    private final boolean terminal;

    FlowExecutionStepStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
