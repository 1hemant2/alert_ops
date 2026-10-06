# Current task plan

## Task: Implement acknowledgement pause

Started: 2026-10-06
Status: Complete

### Goal and scope

Implement task 6 in the [resolution-timeout plan](resolution-timeout-implementation-plan.md#6-implement-acknowledgement-pause): use the saved runtime toggle and exact execution-step token to preserve disabled acknowledgement behavior, or transition enabled runs to `ACKNOWLEDGED`, claim the current owner, pause the next step, and save the resolution deadline. Do not implement explicit resolution, deadline expiry/recovery, Escalate now, or the related UI here.

### Binding decisions

- Disabled acknowledgement remains terminal `OPEN → COMPLETED` with `ACKNOWLEDGED`; enabled acknowledgement becomes `OPEN → ACKNOWLEDGED`.
- Validate the exact sent step and recipient token under the run lock. The acknowledgement owner is the saved execution-step/recipient context.
- For enabled runs, pause the next scheduled step if present, retain later steps as pending, and save the UTC resolution deadline from that step's snapshotted timeout.
- Persist the lifecycle transition and audit facts atomically; cancel or replace wake-ups only after commit. Repeated acknowledgement must not extend the deadline.

### Acceptance criteria

- Disabled acknowledgement still completes as before.
- Enabled acknowledgement pauses progression, including on the final node, and records one resolution deadline and owner.
- Duplicate/repeated acknowledgement is idempotent and does not extend the deadline.
- A stale send or callback cannot bypass the pause.

### Steps

- [x] Inspect the acknowledgement service, run/step repositories, scheduler callbacks, audit service, and existing tests.
- [x] Implement the locked transition, exact-step validation, pause, deadline, ownership, and post-commit wake-up handling.
- [x] Add focused tests for disabled/enabled/final/repeated/stale and in-flight-send paths.
- [x] Run focused tests, full Maven tests, package/UI builds, documentation checks, and independent read-only verification.

### Intended verification and limitations

Verification: focused acknowledgement, message-consumer, and timer tests pass (33 tests); full `mvn -q test` passes (160 tests, 17 expected integration skips); `mvn -q -DskipTests package`, `npm run build`, `git diff --check`, and the 60-line plan check pass. Independent read-only verifier verdict: **Achieved**. PostgreSQL (13), RabbitMQ (3), and Redis (1) tests remain environment-gated; migration/concurrency/deployed checks were not available.

### Resume note

After this task, continue with [explicit resolution](resolution-timeout-implementation-plan.md#7-implement-explicit-resolution). Preserve the agreed snapshot and shared `dueAt` timing decisions.
