# Current task plan

## Task: Snapshot task metadata for runs and emails

Started: 2026-10-08
Status: Complete

### Goal and scope

Complete the remaining backend portion of the webhook/task launch feature by
preserving optional task priority, category, and reference URL in execution
steps and showing those values in escalation emails. Do not change the
webhook-history UI or deployment verification in this task.

Previous task: backend image vulnerability remediation is complete; see the
latest changelog entry and commits `776c4e4`, `6380594`, and `013ac6e`.

### Decision-compliance note

- `Task.priority`, `Task.category`, and `Task.referenceUrl` are the canonical
  source values; copy them once into each `FlowExecutionState` at run start.
- Runtime snapshots are authoritative after start, so later task edits must
  not change an active or historical run.
- Keep priority and category as validated strings because they are user-owned
  labels, and retain the existing HTTP(S) validation for reference URLs.
- Add only the three snapshot columns; do not create a second task metadata
  model or reread the mutable task while sending email.

### Acceptance criteria

- New execution-step rows retain all three optional task fields, including for
  immediate and scheduled starts; existing rows remain readable with nulls.
- Alert emails display the saved metadata without allowing user content to
  create unsafe HTML or links.
- Focused snapshot and notification tests pass, followed by the full Maven
  package and diff checks.
- The launch checklist, feature plan, and changelog record the completed
  backend work and remaining UI/deployed limitations.

### Steps

- [x] Add the migration, entity fields, and start-time snapshot assignment.
- [x] Add safe email rendering and focused regression coverage.
- [x] Run focused tests, the full package, and documentation checks.

### Verification and limitations

Focused snapshot/email tests and `./mvnw -q package` pass with 260 tests,
0 failures/errors, and 17 environment-gated skips. `git diff --check` passes.
The migration was reviewed for existing-row null compatibility, but PostgreSQL
execution and deployed email verification remain pending. The remaining task
and webhook UI work stays outside this task. Independent read-only verification
was skipped because no usable subagent mechanism was available.
