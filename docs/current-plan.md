# Current task plan

## Task: Register live step timers after commit

Started: 2026-10-10
Status: Complete

### Goal and scope

Ensure a newly persisted `SCHEDULED` step is registered with the in-memory
timer while the application is running, and remains recoverable if that
registration fails. Keep the existing staged email styling changes untouched.

### Decision-compliance note

- PostgreSQL `flow_execution_state.dueAt` and `publicationPending` remain the
  durable source of truth; the timer registry owns only wake-up handles.
- Do not add a second due-time column, recurring polling loop, or parallel
  in-memory copy of workflow state.
- Publish the scheduling event through Spring's `@TransactionalEventListener`
  after-commit phase, with fallback execution for transactionless callers.
- Follow the [timer plan](in-memory-timer-implementation-plan.md#durable-scheduling-state):
  validation must read committed PostgreSQL state, including after a bulk start
  update; do not trust the committing transaction's cached escalation.

### Acceptance criteria

- [x] Every committed scheduled step reaches the timer registry or is queued
  for prompt recovery without requiring an application restart.
- [x] Registration failures and timer-capacity limits remain recoverable from
  PostgreSQL and retry while the application is healthy.
- [x] Focused tests cover the after-commit registration path and the recovery
  fallback; existing scheduling behavior remains green.

### Steps

- [x] Inspect the transaction event and recovery wiring for a missed live
  registration.
- [x] Implement the simpler Spring transaction-event listener and focused
  coverage.
- [x] Reproduce a start with a cached pre-start escalation, fix committed-state
  validation, and verify immediate and scheduled start modes.
- [x] Run focused messaging tests, the normal package build, and check the diff.
- [x] Record the result and live-app verification limitation in `CHANGELOG.md`.

### Intended verification

The new regression failed in all three start modes before the fresh read and
passed afterward, including publication at the saved due time. Seven focused
PostgreSQL cases pass: start modes, zero delay, rollback, fallback and recovery.
The backend package passes with an explicit Byte Buddy test agent, including
the focused messaging unit suite; `git diff --check` passes. PostgreSQL tests
used a temporary schema and a simulated broker. The running local app needs a
restart to load this fix; real broker/email verification remains outstanding.
