-- Older releases used RUNNING for an actively processing escalation.
UPDATE escalation
SET status = 'OPEN'
WHERE status = 'RUNNING';

-- Do not silently invent a lifecycle state for legacy rows with no status.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM escalation WHERE status IS NULL) THEN
        RAISE EXCEPTION 'Cannot constrain escalation status: existing rows have NULL status';
    END IF;
END
$$;

ALTER TABLE escalation
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE escalation
    ADD CONSTRAINT escalation_status_check
    CHECK (status IN ('IDLE', 'SCHEDULED', 'OPEN', 'COMPLETED', 'CANCELLED', 'START_FAILED'));

ALTER TABLE escalation
    ADD CONSTRAINT escalation_resolution_type_check
    CHECK (resolution_type IS NULL OR resolution_type IN ('ACKNOWLEDGED', 'EXHAUSTED'));
