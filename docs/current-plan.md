# Current task plan

## Task: Make naming guidance business-focused

Started: 2026-10-07
Status: Complete locally; PostgreSQL/deployed verification pending

### Goal and scope

Update `AGENTS.md` so names describe the business responsibility or user-visible
outcome before the implementation mechanism. Preserve the current timeout
implementation and all existing naming examples unless this guidance requires
an explicit correction.

### Decision-compliance note

- This is documentation-only; no database field, API, timer, or lifecycle
  behavior changes.
- Keep the existing rule for concise names, while adding business meaning as
  the first naming criterion and using mechanism terms only when necessary.

### Acceptance criteria

- `AGENTS.md` explains business-focused naming with concise examples.
- Existing naming guidance remains internally consistent.
- Documentation/diff checks and the normal backend build pass.

### Steps

- [x] Add business-focused naming guidance and examples to `AGENTS.md`.
- [x] Check the guidance against the current timeout names and existing rules.
- [x] Run documentation/diff checks and the normal backend build.
- [x] Update the changelog and record verification limits.

### Intended verification and limitations

`AGENTS.md` guidance review, `git diff --check`, plan-length check, and
`mvn -q -DskipTests compile` passed. No runtime behavior changed, so the full
test suite was not rerun for this documentation-only task. PostgreSQL/RabbitMQ/
Redis and deployed checks remain environment-gated. No independent read-only
verifier mechanism was available in this session, so the task is not claimed as
independently verified.

### Resume note

The current timeout naming refactor and earlier staged/unstaged changes remain
in the working tree. Preserve them while updating only the naming guidance.
