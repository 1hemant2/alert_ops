-- Replace the independent execution and notification states with one explicit lifecycle.
ALTER TABLE flow_execution_state
    ADD COLUMN status VARCHAR(32);

UPDATE flow_execution_state
SET status = CASE
    WHEN execution_state = 'TERMINAL' AND notification_state = 'SENT' THEN 'SENT'
    WHEN execution_state = 'TERMINAL' AND notification_state = 'FAILED' THEN 'FAILED'
    WHEN execution_state = 'PENDING'
         AND COALESCE(notification_state, 'NOT_SENT') = 'NOT_SENT' THEN 'PENDING'
    WHEN execution_state = 'TERMINAL' AND notification_state = 'NOT_SENT' THEN 'SKIPPED'
    WHEN execution_state IN ('ACTIVE', 'PROCESSING')
         AND COALESCE(notification_state, 'NOT_SENT') = 'NOT_SENT' THEN 'SCHEDULED'
    ELSE NULL
END;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flow_execution_state WHERE status IS NULL) THEN
        RAISE EXCEPTION 'Cannot unify flow execution step status: unsupported legacy state combination exists';
    END IF;
END
$$;

-- A legacy worker that was processing a message when the schema changed must be recoverable.
UPDATE flow_execution_state
SET publication_pending = TRUE,
    due_at = COALESCE(due_at, CURRENT_TIMESTAMP)
WHERE execution_state = 'PROCESSING'
  AND status = 'SCHEDULED';

DROP INDEX IF EXISTS flow_execution_state_pending_publication_idx;

ALTER TABLE flow_execution_state
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE flow_execution_state
    ADD CONSTRAINT flow_execution_state_status_check
    CHECK (status IN ('PENDING', 'SCHEDULED', 'PAUSED', 'SENDING', 'SENT', 'FAILED', 'SKIPPED'));

ALTER TABLE flow_execution_state
    DROP COLUMN execution_state,
    DROP COLUMN notification_state;

CREATE INDEX flow_execution_state_pending_publication_idx
    ON flow_execution_state (due_at, id)
    WHERE status = 'SCHEDULED'
      AND publication_pending = TRUE;
