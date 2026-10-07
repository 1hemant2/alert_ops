# Current task plan

## Task: Implement explicit resolution

Started: 2026-10-07
Status: Complete locally; PostgreSQL/deployed verification pending

### Goal and scope

Implement task 7 in the [resolution-timeout plan](resolution-timeout-implementation-plan.md#7-implement-explicit-resolution): add an explicit resolve operation for authenticated same-team members and the current acknowledging email recipient. Transition an enabled run from `ACKNOWLEDGED` to `RESOLVED`, persist the resolving actor/time, skip remaining unsent steps, audit the change, and cancel paused wake-ups after commit. Do not implement deadline expiry/recovery, Escalate now, or the related UI here.

### Binding decisions

- `RESOLVED` is the self-describing terminal lifecycle state; `resolutionType` remains null and active acknowledgement ownership is cleared.
- Team resolution requires an authenticated member of the escalation's selected team. Email resolution requires the exact saved acknowledgement token, recipient, and acknowledged execution step.
- Resolution is allowed strictly before the saved UTC resolution deadline. A repeated resolution by the same actor returns the saved result without changing state; other actors receive a conflict.
- Skip all remaining `PENDING`, `SCHEDULED`, or `PAUSED` steps and cancel their in-memory wake-ups only after the transaction commits. Persist the transition, resolving actor/time, and audit event atomically; retain the acknowledged step/source/token-hash facts in safe audit metadata.

### Acceptance criteria

- Authenticated same-team and current acknowledging recipient resolution both succeed before the deadline.
- Resolution stores actor/time, changes `ACKNOWLEDGED → RESOLVED`, clears active acknowledgement ownership, and skips unsent steps.
- Repeated same-actor resolution is idempotent; foreign-team, obsolete-owner, late, and terminal requests do not change state.
- A concurrent resolution/timeout or delivery callback has one winner and cannot produce duplicate audit or delivery work.

### Steps

- [x] Inspect the resolution model, acknowledgement token flow, team authorization, repositories, scheduler, audit, controllers, and tests.
- [x] Implement locked team/token resolution, status/actor fields, deadline and owner checks, step skipping, audit, and post-commit wake-up cancellation.
- [x] Add focused tests for authorization, deadline boundaries, idempotency, stale ownership, terminal states, audit metadata, and post-commit cancellation; rely on PostgreSQL integration coverage for concurrency/rollback when available.
- [x] Run focused tests, full Maven tests, package/UI builds, documentation checks, and independent read-only verification.

### Intended verification and limitations

Verification: focused resolution/security tests pass; full `mvn -q test` passes with 177 tests, 0 failures/errors, and 17 environment-gated skips; backend package, UI build, `git diff --check`, plan-length, and redundant-field checks pass. Independent read-only verifier verdict: **Inconclusive** only for PostgreSQL-backed migration/rollback/concurrency because PostgreSQL/RabbitMQ/Redis integrations are skipped and Docker is unavailable. The verifier found no local implementation or test regression.

### Resume note

After this task, continue with [deadline expiry and recovery](resolution-timeout-implementation-plan.md#8-implement-deadline-expiry-and-recovery). Preserve the agreed snapshot, shared `dueAt`, and active-owner semantics.
