# Current task plan

## Task: Extend escalation audit event coverage

Started: 2026-10-08
Status: Complete

### Goal and scope

Record the missing user-visible escalation lifecycle events at the operation
that owns each state change. Keep the generic audit model, avoid a timeline
read API or UI in this task, and do not commit the changes.

### Decision-compliance note

- Audit rows remain append-only and join the owning lifecycle transaction.
- PostgreSQL domain state remains canonical; audit metadata preserves step and
  recipient facts without reconstructing history from current flow definitions.
- Do not add an outbox, second event store, or duplicate status/timestamp fields.
- Retry and duplicate paths must record meaningful attempts without inventing
  duplicate successful lifecycle events.

### Acceptance criteria

- Creation, send acceptance/failure/retry, acknowledgement/deadline, manual
  escalation, resolution/timeout, scheduling, start, cancellation, completion,
  exhaustion, and start failure have safe audit coverage.
- Events include actor/system, affected step or recipient where applicable, and
  stable safe metadata; raw tokens and diagnostics stay out of history.
- Existing idempotency and transaction behavior remains unchanged.
- Focused tests, package build, and whitespace checks pass.

### Steps

- [x] Inventory existing event ownership and add only missing actions/calls.
- [x] Add focused audit assertions for new meaningful events.
- [x] Run verification and update readiness/changelog without committing.

### Verification and limitations

- Focused lifecycle, consumer, acknowledgement, resolution, timeout, manual
  escalation, start-failure, and audit tests passed.
- `./mvnw -q package` and `git diff --check` passed. PostgreSQL/RabbitMQ/Redis
  integration tests remain environment-gated and deployed verification is still
  pending.
- No independent read-only verifier was available; no commit was created.
