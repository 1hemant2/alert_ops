ALTER TABLE escalation ADD COLUMN scheduled_start_retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE escalation ADD COLUMN scheduled_start_next_retry_at TIMESTAMPTZ;
