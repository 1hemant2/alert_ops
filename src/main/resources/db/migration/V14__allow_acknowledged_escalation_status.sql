ALTER TABLE escalation
    DROP CONSTRAINT escalation_status_check;

ALTER TABLE escalation
    ADD CONSTRAINT escalation_status_check
    CHECK (status IN ('IDLE', 'SCHEDULED', 'OPEN', 'ACKNOWLEDGED', 'COMPLETED', 'CANCELLED', 'START_FAILED'));
