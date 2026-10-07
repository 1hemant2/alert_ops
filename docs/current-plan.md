# Current task plan

## Task: Add the escalation activity history read API

Started: 2026-10-08
Status: Complete

### Goal and scope

Expose the saved escalation audit history to authorized team members. Reuse the
existing audit table and lifecycle metadata; keep this task read-only, avoid
the timeline UI, and do not commit the changes.

### Decision-compliance note

- The audit table is the canonical history source; the API must not rebuild
  history from current escalation or flow state.
- Access is authorized through the existing authenticated team context and the
  escalation's owning team; recipient tokens do not grant history access.
- Do not add a second event store, duplicate lifecycle fields, or raw token,
  secret, diagnostic, or stack-trace output.
- Preserve the audit row's stable ordering and safe metadata while allowing
  pagination over the existing append-only records.

### Acceptance criteria

- An authenticated member can read one escalation's history only when it
  belongs to the selected team.
- Results are safely mapped, ordered by occurred time and audit id, and paged
  without leaking raw tokens, secrets, or diagnostics.
- Anonymous, token-only, missing, and foreign-team requests are rejected using
  existing access behavior; reads do not create state or audit events.
- Focused API/service tests, package build, and whitespace checks pass.

### Steps

- [x] Design the smallest safe repository query, DTO, service, and endpoint.
- [x] Add authorization, pagination, mapping, and leakage tests.
- [x] Run verification and update readiness/changelog without committing.

### Verification and limitations

- Focused history service/controller tests passed for authorization, pagination,
  stable ordering, actor mapping, and sensitive-detail removal.
- `./mvnw -q package` passed: 232 tests, 0 failures/errors, and 17
  environment-gated skips. `git diff --check` passed.
- PostgreSQL/deployed verification and independent read-only verification remain
  unavailable; no commit was created.
