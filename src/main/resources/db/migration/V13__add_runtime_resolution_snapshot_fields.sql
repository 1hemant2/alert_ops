ALTER TABLE flow_execution_state
    ADD COLUMN resolution_timeout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN resolution_timeout NUMERIC(21, 0);

ALTER TABLE flow_execution_state
    ADD CONSTRAINT flow_execution_state_resolution_timeout_positive
    CHECK (resolution_timeout IS NULL OR resolution_timeout > 0);

ALTER TABLE escalation
    ADD COLUMN acknowledged_step_id UUID,
    ADD COLUMN resolution_deadline TIMESTAMPTZ;

ALTER TABLE escalation_acknowledgement_token
    ADD COLUMN execution_step_id UUID;

CREATE INDEX idx_escalation_ack_token_execution_step
    ON escalation_acknowledgement_token (execution_step_id);
