# Current task plan

## Task: Complete webhook backend readiness

Started: 2026-10-08
Status: Complete

### Goal and scope

Align webhook and manual task validation, harden replay/rollback and team
isolation behavior, verify rate and request-size limits, and add focused
backend tests. Keep UI follow-up and deployment verification out of scope.

### Decision-compliance note

- The webhook event's saved JSON and linked task/escalation rows remain the
  durable source of truth; retries must reuse the unique webhook/event key.
- Reuse the existing task fields and domain enums; do not add duplicate
  metadata columns or a second event store.
- Preserve the existing one-transaction webhook create/start boundary and
  team-scoped flow selection; rollback must remove all linked rows together.
- Keep Redis as the distributed rate-limit source when available; any local
  fallback must be explicit and tested as a degraded single-instance mode.

### Acceptance criteria

- Manual and webhook task limits are aligned and server-side validation rejects
  oversized or invalid values before persistence.
- Required fields, same-team flow selection, secret rotation/disable, replay,
  changed-payload conflicts, rollback, size limits, and rate limits have tests.
- Webhook-created tasks and runs preserve the agreed metadata and event links.
- Focused webhook tests, full Maven package, and whitespace checks pass.

### Steps

- [x] Map current webhook behavior, limits, and missing focused coverage.
- [x] Implement the smallest validation/limit fixes and add focused tests.
- [x] Run verification and update readiness/changelog without committing.

### Verification and limitations

- Focused webhook, payload-filter, and task validation tests pass.
- `./mvnw -q package` passes: 260 tests, 0 failures, 0 errors, 17
  environment-gated skips. `git diff --check` passes.
- PostgreSQL concurrent replay/rollback, Redis multi-instance behavior, and
  deployed verification remain open. The local rate-limit fallback and
  failure path are unit-tested only.
- Independent read-only verification was skipped because no usable subagent
  mechanism was available. Changes remain uncommitted because no commit was
  requested.
