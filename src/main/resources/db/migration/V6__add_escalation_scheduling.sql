ALTER TABLE escalation ADD COLUMN scheduled_start_at TIMESTAMPTZ;
ALTER TABLE escalation ADD COLUMN schedule_timezone VARCHAR(80);
ALTER TABLE escalation ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE INDEX escalation_scheduled_start_idx
    ON escalation (scheduled_start_at, id)
    WHERE status = 'SCHEDULED';
