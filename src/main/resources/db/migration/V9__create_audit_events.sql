CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(128) NOT NULL,
    previous_state VARCHAR(64),
    new_state VARCHAR(64),
    user_id UUID,
    user_email VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500),
    metadata TEXT
);

CREATE INDEX audit_event_entity_time_idx
    ON audit_event (entity_type, entity_id, occurred_at, id);
