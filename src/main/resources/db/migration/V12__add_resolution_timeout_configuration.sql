ALTER TABLE flow
    ADD COLUMN resolution_timeout_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE node
    ADD COLUMN resolution_timeout NUMERIC(21, 0);

ALTER TABLE node
    ADD CONSTRAINT node_resolution_timeout_positive
    CHECK (resolution_timeout IS NULL OR resolution_timeout > 0);
