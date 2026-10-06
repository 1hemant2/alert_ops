# Current task plan

## Task: Persist a consistent runtime snapshot

Started: 2026-10-06
Status: Complete

### Goal and scope

Implement task 5 in the [resolution-timeout plan](resolution-timeout-implementation-plan.md#5-persist-a-consistent-runtime-snapshot): copy the flow toggle and each node's resolution timeout into durable execution-step state at immediate or scheduled start, bind acknowledgement tokens to exact execution steps, and add storage for active acknowledgement ownership while reusing `dueAt` as the canonical shared acknowledgement/delivery deadline. Do not implement acknowledgement pause, explicit resolution, timeout expiry, or manual escalation behavior here.

### Acceptance criteria

- Each started execution step contains an immutable snapshot of the flow toggle and its node resolution timeout; later flow edits do not change the run.
- Acknowledgement tokens persist and validate their exact execution-step ID, so same-recipient nodes cannot share an action target.
- `dueAt` remains the durable per-step acknowledgement/delivery deadline, active acknowledgement ownership is durable, and failed start writes remain inside the existing transaction.

### Steps

- [x] Inspect the start transaction, execution-step state, acknowledgement token flow, persistence schema, and affected tests.
- [x] Add the snapshot/ownership fields, migration, start-path propagation, and exact token binding.
- [x] Remove the redundant acknowledgement-deadline field and align the plan with `dueAt` as the canonical shared wait boundary.
- [x] Re-run focused tests, normal builds, documentation checks, and independent read-only verification after the correction.

### Verification and limitations

- Verification: after removing the redundant acknowledgement-deadline field, focused execution-start, acknowledgement, and consumer tests passed; full `mvn -q test` passed; `mvn -q -DskipTests package` passed; `npm run build` passed; documentation anchors/whitespace/plan-length, forbidden-reference, and diff checks passed. PostgreSQL (13), RabbitMQ (3), and Redis (1) integration tests remain environment-gated and skipped. Independent read-only verifier verdict: **Achieved**; it confirmed the corrected acceptance criteria, scope boundaries, and no regressions. PostgreSQL rollback/migration execution remained unavailable.

### Requested documentation follow-up

- [x] Strengthen AGENTS.md to require decision-first review of canonical fields and cleanup of conflicting implementation when an agreed decision is clarified.

### Resume note

After this task, continue with [acknowledgement pause](resolution-timeout-implementation-plan.md#6-implement-acknowledgement-pause). Do not implement acknowledgement pause, explicit resolution, timeout expiry, or Escalate now behavior in this task.
