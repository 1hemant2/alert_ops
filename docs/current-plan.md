# Current task plan

## Task: Simplify manual Escalate now service

Started: 2026-10-08
Status: Complete locally

### Goal and scope

Remove redundant helpers from `EscalationManualActionService`, preserve its
authorization, locking, lifecycle, token, audit, and preview behavior, and add
concise guidance to `AGENTS.md` for minimal readable code.

### Decision-compliance note

- Keep PostgreSQL-backed escalation/step state and existing source/target
  validation as the canonical lifecycle decision.
- Preserve recipient-token scope, response-window checks, idempotent audit
  handling, and the existing public endpoints.
- Do not split the service into more classes or add a generic framework for a
  small set of manual-action paths.

### Acceptance criteria

- Remove only helpers that add no meaningful business clarity or protection.
- Keep all meaningful security, concurrency, null-safety, and lifecycle checks.
- `AGENTS.md` requires the smallest readable and reviewable implementation.
- Focused tests, package build, and whitespace checks pass.

### Steps

- [x] Simplify the service and update its tests if behavior-focused coverage needs clarification.
- [x] Update `AGENTS.md`, plan, and changelog.
- [x] Run verification and review the final diff.

### Intended verification and limitations

The service had no unreachable private method; four low-value helpers were
removed and generic helpers were renamed to explicit Escalate now names.
Manual-action/security tests, the backend package build, and `git diff --check`
pass. PostgreSQL/deployed behavior remains environment-gated; no usable
independent read-only verifier is available in this environment.
