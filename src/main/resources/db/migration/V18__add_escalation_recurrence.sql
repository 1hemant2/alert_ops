ALTER TABLE escalation
    ADD COLUMN repeat_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN next_repeat_at TIMESTAMPTZ,
    ADD COLUMN repeat_source_id UUID;

ALTER TABLE escalation
    ADD CONSTRAINT escalation_repeat_type_check
    CHECK (repeat_type IN ('NONE', 'DAILY', 'WEEKLY'));

ALTER TABLE escalation
    ADD CONSTRAINT escalation_repeat_source_check
    CHECK (repeat_source_id IS NULL OR repeat_type = 'NONE');

ALTER TABLE escalation
    ADD CONSTRAINT escalation_repeat_source_fk
    FOREIGN KEY (repeat_source_id) REFERENCES escalation(id);

CREATE INDEX escalation_repeat_recovery_idx
    ON escalation (next_repeat_at, id)
    WHERE repeat_type <> 'NONE' AND next_repeat_at IS NOT NULL;

CREATE INDEX escalation_repeat_source_idx
    ON escalation (repeat_source_id, scheduled_start_at, id)
    WHERE repeat_source_id IS NOT NULL;

CREATE UNIQUE INDEX escalation_repeat_occurrence_unique_idx
    ON escalation (repeat_source_id, scheduled_start_at)
    WHERE repeat_source_id IS NOT NULL;
