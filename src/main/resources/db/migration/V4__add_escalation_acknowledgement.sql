ALTER TABLE escalation
    ADD COLUMN acknowledged_at TIMESTAMPTZ;

CREATE TABLE escalation_acknowledgement_token (
    id UUID PRIMARY KEY,
    escalation_id UUID NOT NULL REFERENCES escalation(id) ON DELETE CASCADE,
    recipient_email VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_escalation_acknowledgement_token_escalation
    ON escalation_acknowledgement_token (escalation_id);
