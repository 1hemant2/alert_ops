ALTER TABLE escalation_acknowledgement_token
    ADD COLUMN capability VARCHAR(32) NOT NULL DEFAULT 'ACKNOWLEDGE',
    ADD COLUMN expected_target_step_id UUID;

ALTER TABLE escalation_acknowledgement_token
    ADD CONSTRAINT escalation_ack_token_capability_valid
    CHECK (capability IN ('ACKNOWLEDGE', 'ESCALATE_NOW'));

CREATE INDEX idx_escalation_action_token_target
    ON escalation_acknowledgement_token (expected_target_step_id);
