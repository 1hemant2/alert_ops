ALTER TABLE escalation ADD COLUMN scheduled_by_user_id UUID;
ALTER TABLE escalation ADD COLUMN scheduled_by_user_email VARCHAR(320);

CREATE TABLE escalation_start_failure_notification (
    id UUID PRIMARY KEY,
    escalation_id UUID NOT NULL REFERENCES escalation(id),
    recipient_user_id UUID,
    recipient_email VARCHAR(320) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    last_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    sent_at TIMESTAMP(6) WITH TIME ZONE,
    last_error VARCHAR(500),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT escalation_start_failure_notification_status_ck
        CHECK (status IN ('PENDING', 'SENDING', 'SENT')),
    CONSTRAINT escalation_start_failure_notification_recipient_uq
        UNIQUE (escalation_id, recipient_email)
);

CREATE INDEX escalation_start_failure_notification_recovery_idx
    ON escalation_start_failure_notification (status, next_attempt_at, id);
