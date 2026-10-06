# Current task plan

## Task: Unify execution-step statuses

Started: 2026-10-06
Status: Complete

### Goal and scope

Implement the next pending task in the [resolution-timeout plan](resolution-timeout-implementation-plan.md#3-unify-execution-step-statuses): replace the two independent execution-step status strings with one explicit persisted enum across the backend, database, recovery/queue paths, DTOs, tests, and UI mappings. Preserve existing behavior; resolution-timeout behavior itself remains out of scope.

### Acceptance criteria

- Each execution step has exactly one supported persisted status and the database rejects unsupported values.
- Delivery, retry, duplicate-message, restart-recovery, and disabled-acknowledgement behavior remain correct.
- All affected API/UI mappings and focused tests use the single status without unsupported combinations.

### Steps

- [x] Inspect the current step model, persistence schema, repositories, message/recovery paths, DTOs, UI, and tests.
- [x] Add the enum, migration/constraints, and coordinated backend/UI changes.
- [x] Add or update focused tests for state transitions, persistence, duplicate delivery, retry, recovery, and disabled acknowledgement.
- [x] Run focused tests, normal builds, documentation checks, and review the final diff.

### Verification and limitations

- Passed: focused backend tests, `mvn test`, `mvn package -DskipTests`, `npm run build`, `git diff --check`, documentation target/anchor checks, 32-line plan-length check, and focused diff review.
- PostgreSQL integration: skipped by the environment gate; `StepSchedulingPostgresIntegrationTest` reported 13 skipped tests because `POSTGRES_INTEGRATION_TEST` and database variables were unavailable.
- Independent read-only verifier: Inconclusive. Two verifier attempts were dispatched, but neither returned a report after repeated waits; both were shut down without modifying repository state. No independent verifier evidence is available.

### Resume note

After this task, continue with [flow/node timing configuration](resolution-timeout-implementation-plan.md#4-add-agreed-flow-node-timing-configuration). Resolution timeout behavior itself remains separate.
