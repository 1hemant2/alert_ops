ALTER TABLE tasks ADD COLUMN source VARCHAR(120);
UPDATE tasks SET source = 'Manual' WHERE source IS NULL;
ALTER TABLE tasks ALTER COLUMN source SET NOT NULL;
ALTER TABLE tasks ADD COLUMN priority VARCHAR(20);
ALTER TABLE tasks ADD COLUMN category VARCHAR(80);
ALTER TABLE tasks ADD COLUMN reference_url VARCHAR(2048);

ALTER TABLE flow_execution_state ADD COLUMN task_name VARCHAR(120);
ALTER TABLE flow_execution_state ADD COLUMN task_source VARCHAR(120);

CREATE TABLE webhook_configuration (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES team(id),
    default_flow_id UUID NOT NULL REFERENCES flow(id),
    name VARCHAR(120) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_triggered_at TIMESTAMPTZ
);

CREATE INDEX webhook_configuration_team_idx ON webhook_configuration(team_id);

CREATE TABLE webhook_event (
    id UUID PRIMARY KEY,
    webhook_id UUID NOT NULL REFERENCES webhook_configuration(id),
    event_id VARCHAR(255) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    task_id UUID NOT NULL REFERENCES tasks(id),
    escalation_id UUID NOT NULL REFERENCES escalation(id),
    CONSTRAINT webhook_event_identity_uq UNIQUE (webhook_id, event_id)
);

CREATE INDEX webhook_event_webhook_received_idx ON webhook_event(webhook_id, received_at DESC);
