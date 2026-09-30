ALTER TABLE tasks
    ALTER COLUMN description TYPE TEXT;

ALTER TABLE flow_execution_state
    ALTER COLUMN task_details TYPE TEXT;
