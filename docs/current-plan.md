# Current task plan

## Task: Complete incident lifecycle transition coverage

Started: 2026-10-08
Status: Complete

### Goal and scope

Close the remaining local launch-checklist gap by adding focused backend tests
for lifecycle transitions, authorization, stale callbacks, idempotency, and
concurrency. Keep deployment verification out of scope and do not commit.

### Decision-compliance note

- PostgreSQL conditional updates and row locks are the lifecycle source of
  truth; in-memory callbacks must not decide ownership.
- Use the existing status/resolution enums and canonical lifecycle fields; do
  not add parallel state or timing fields for tests.
- Audit events must remain part of the same lifecycle transaction where the
  existing implementation records them.

### Acceptance criteria

- Focused tests cover the remaining start/schedule, start/cancel,
  start/reschedule, acknowledgement/send, and failure/cancellation races.
- Tests cover authorization, stale callbacks, repeated actions, terminal
  protections, and the agreed conflict/idempotency responses.
- Existing behavior remains unchanged unless a test exposes a real defect.
- Focused tests, full Maven package, and whitespace checks pass.

### Steps

- [x] Map existing lifecycle coverage and identify only missing cases.
- [x] Add focused service/controller/integration tests or fix defects exposed
  by those tests.
- [x] Run verification and update the checklist/changelog without committing.

### Verification and limitations

- Lifecycle-focused service, controller, scheduling, acknowledgement,
  resolution, manual-action, and timeout tests passed.
- `./mvnw -q package` and `git diff --check` passed. PostgreSQL/deployed
  verification remains unavailable because the integration environment is
  gated, and no independent read-only verifier was available.
- Changes remain uncommitted because the user did not request a commit.
