ALTER TABLE escalation
    ADD COLUMN resolved_by VARCHAR(320),
    ADD COLUMN resolved_at TIMESTAMPTZ;

ALTER TABLE escalation
    DROP CONSTRAINT escalation_status_check;

ALTER TABLE escalation
    ADD CONSTRAINT escalation_status_check
    CHECK (status IN ('IDLE', 'SCHEDULED', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'COMPLETED', 'CANCELLED', 'START_FAILED'));
