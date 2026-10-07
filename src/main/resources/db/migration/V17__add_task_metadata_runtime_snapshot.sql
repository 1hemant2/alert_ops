ALTER TABLE flow_execution_state
    ADD COLUMN task_priority VARCHAR(20),
    ADD COLUMN task_category VARCHAR(80),
    ADD COLUMN task_reference_url VARCHAR(2048);
